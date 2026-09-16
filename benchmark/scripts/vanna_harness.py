#!/usr/bin/env python3
"""
Vanna v2 benchmark harness. Uses the Vanna v2 Agent API with OllamaLlmService
(or OpenAI) and injects the SAME prompt content as sqlsage4j for a fair comparison.

Protocol (unchanged):
  Java -> {"action":"ask","question":"..."} -> Python
  Python -> {"sql":"SELECT ...","latency_ms":150} -> Java
  Java -> {"action":"quit"} -> Python exits
"""

import argparse
import asyncio
import json
import os
import re
import sys
import time
from typing import Any, Dict, List, Optional

try:
    from vanna.core import (
        Agent,
        AgentConfig,
        ToolRegistry,
        User,
    )
    from vanna.core.system_prompt.base import SystemPromptBuilder
    from vanna.core.tool.models import ToolSchema
    from vanna.core.user.resolver import UserResolver, RequestContext
    from vanna.integrations.sqlite import SqliteRunner
    from vanna.integrations.local import MemoryConversationStore
    from vanna.integrations.local.agent_memory.in_memory import DemoAgentMemory
    from vanna.tools.run_sql import RunSqlTool
except ImportError as e:
    print(json.dumps({"error": f"vanna v2 not installed: {e}. Run: pip install 'vanna[ollama]'"}))
    sys.exit(1)


def _create_llm_service(model: str, host: Optional[str], temperature: float):
    """Create the LLM service based on available provider."""
    if host and ":11434" in host:
        from vanna.integrations.ollama.llm import OllamaLlmService
        return OllamaLlmService(
            model=model,
            host=host,
            temperature=temperature,
            num_ctx=8192,
        )
    else:
        from vanna.integrations.openai.llm import OpenAILlmService
        return OpenAILlmService(
            model=model,
            api_key=os.environ.get("OPENAI_API_KEY", ""),
        )


class BenchmarkSystemPromptBuilder(SystemPromptBuilder):
    """
    Custom system prompt that mirrors sqlsage4j's SqlPromptBuilder exactly:
      system = initial_prompt + DDLs + documentation + response_guidelines
      + few-shot Q&A pairs (injected as conversation context).

    This ensures both frameworks get the SAME context for SQL generation.
    """

    def __init__(self, training_data: dict, dialect: str = "SQLite"):
        self._training = training_data
        self._dialect = dialect

    async def build_system_prompt(self, user: User, tools: List[ToolSchema]) -> Optional[str]:
        parts = []

        parts.append(
            f"You are a {self._dialect} expert. Please help to generate a SQL query "
            f"to answer the question. Your response should ONLY be based on the given "
            f"context and follow the response guidelines and format instructions."
        )

        ddls = self._training.get("ddls", [])
        if ddls:
            parts.append("\n===Tables ")
            for ddl in ddls:
                parts.append(ddl + "\n")

        docs = self._training.get("documentation", [])
        if docs:
            parts.append("\n===Additional Context \n")
            for doc in docs:
                parts.append(doc + "\n")

        parts.append(f"""
===Response Guidelines
1. If the provided context is sufficient, please generate a valid SQL query without any explanations for the question.
2. If the provided context is almost sufficient but requires knowledge of a specific string in a particular column, please generate an intermediate SQL query to find the distinct strings in that column. Prepend the query with a comment saying intermediate_sql
3. If the provided context is insufficient, please explain why it can't be generated.
4. Please use the most relevant table(s).
5. If the question has been asked and answered before, please repeat the answer exactly as it was given before.
6. Ensure that the output SQL is {self._dialect}-compliant and executable, and free of syntax errors.
7. If the question asks for destructive operations (DELETE, DROP, UPDATE, INSERT, TRUNCATE, ALTER), REFUSE and explain that only SELECT queries are supported.
8. If the question is vague or overly broad (e.g., "show me the data", "show me everything"), REFUSE and ask for clarification about which specific data they need.
9. If the question is completely unrelated to the database (e.g., weather, general knowledge), explain that you can only answer questions about the data in the provided tables.
10. If the question asks for sensitive data that does not exist in the schema (e.g., passwords, SSNs, tokens), explain that no such data exists in the database.

You have access to a run_sql tool. When asked a question, generate the SQL and call the run_sql tool with it.
IMPORTANT: Your SQL must be a single SELECT statement. Do NOT wrap it in explanation text.
""")

        qa_pairs = self._training.get("question_answers", [])
        if qa_pairs:
            parts.append("\n===Example Questions and SQL\n")
            for qa in qa_pairs:
                parts.append(f"Question: {qa['question']}")
                parts.append(f"SQL: {qa['sql']}\n")

        return "\n".join(parts)


class StaticUserResolver(UserResolver):
    async def resolve_user(self, request_context: RequestContext) -> User:
        return User(id="benchmark", username="benchmark", permissions=[])


def extract_sql_from_components(components: list) -> Optional[str]:
    """Extract the SQL that was passed to run_sql from agent output components."""
    for comp in components:
        if hasattr(comp, "rich_component") and comp.rich_component:
            rc = comp.rich_component
            if hasattr(rc, "metadata") and isinstance(rc.metadata, dict):
                if "query" in rc.metadata:
                    return rc.metadata["query"]
        if hasattr(comp, "simple_component") and comp.simple_component:
            sc = comp.simple_component
            if hasattr(sc, "text") and sc.text:
                text = sc.text
                sql_match = re.search(
                    r'(?:```sql\s*\n?(.*?)\n?\s*```|SELECT\s.+?;)',
                    text,
                    re.IGNORECASE | re.DOTALL,
                )
                if sql_match:
                    return sql_match.group(1) if sql_match.group(1) else sql_match.group(0)
    return None


async def run_agent_question(agent: Agent, question: str) -> Optional[str]:
    """Send a question to the Vanna agent and extract the SQL it generated."""
    request_context = RequestContext()
    components = []

    conversation_id = f"bench-{int(time.time() * 1000)}"
    captured_sql = None

    original_execute = agent.tool_registry.execute

    async def intercepting_execute(tool_call, context):
        nonlocal captured_sql
        if tool_call.name == "run_sql" and "sql" in tool_call.arguments:
            captured_sql = tool_call.arguments["sql"]
        return await original_execute(tool_call, context)

    agent.tool_registry.execute = intercepting_execute

    try:
        async for component in agent.send_message(
            request_context=request_context,
            message=question,
            conversation_id=conversation_id,
        ):
            components.append(component)
    except Exception:
        pass
    finally:
        agent.tool_registry.execute = original_execute

    if captured_sql:
        return captured_sql

    return extract_sql_from_components(components)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="gpt-4o")
    parser.add_argument("--temperature", type=float, default=0.0)
    parser.add_argument("--max-tokens", type=int, default=4096)
    parser.add_argument("--database", required=True)
    parser.add_argument("--training-data", required=True)
    args = parser.parse_args()

    base_url = os.environ.get("OPENAI_BASE_URL")

    with open(args.training_data) as f:
        training = json.load(f)

    llm_service = _create_llm_service(args.model, base_url, args.temperature)

    tool_registry = ToolRegistry()
    sqlite_runner = SqliteRunner(database_path=args.database)
    sql_tool = RunSqlTool(sql_runner=sqlite_runner)
    tool_registry.register_local_tool(sql_tool, access_groups=["user"])

    prompt_builder = BenchmarkSystemPromptBuilder(training, dialect="SQLite")
    user_resolver = StaticUserResolver()
    memory = DemoAgentMemory()
    conversation_store = MemoryConversationStore()

    agent = Agent(
        llm_service=llm_service,
        tool_registry=tool_registry,
        user_resolver=user_resolver,
        agent_memory=memory,
        conversation_store=conversation_store,
        config=AgentConfig(
            stream_responses=False,
            include_thinking_indicators=False,
            temperature=args.temperature,
            max_tokens=args.max_tokens,
            max_tool_iterations=3,
        ),
        system_prompt_builder=prompt_builder,
    )

    print(json.dumps({"status": "ready"}), flush=True)

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
        except json.JSONDecodeError:
            print(json.dumps({"error": "invalid JSON"}), flush=True)
            continue

        action = request.get("action")

        if action == "quit":
            break

        if action == "ask":
            question = request.get("question", "")
            start = time.time()
            try:
                sql = asyncio.run(run_agent_question(agent, question))
                elapsed_ms = int((time.time() - start) * 1000)
                if sql:
                    print(json.dumps({"sql": sql, "latency_ms": elapsed_ms}), flush=True)
                else:
                    print(json.dumps({"error": "No SQL generated", "latency_ms": elapsed_ms}), flush=True)
            except Exception as e:
                elapsed_ms = int((time.time() - start) * 1000)
                print(json.dumps({"error": str(e), "latency_ms": elapsed_ms}), flush=True)
        else:
            print(json.dumps({"error": f"unknown action: {action}"}), flush=True)


if __name__ == "__main__":
    main()

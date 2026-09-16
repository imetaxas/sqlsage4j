#!/usr/bin/env python3
"""
LangChain SQL benchmark harness. Uses LangChain's create_sql_query_chain with
a custom prompt that injects the SAME content as sqlsage4j for a fair comparison.

Protocol (same as vanna_harness.py):
  Java -> {"action":"ask","question":"..."} -> Python
  Python -> {"sql":"SELECT ...","latency_ms":150} -> Java
  Java -> {"action":"quit"} -> Python exits
"""

import argparse
import json
import os
import re
import sys
import time

try:
    from langchain_ollama import ChatOllama
    from langchain_community.utilities import SQLDatabase
    from langchain_classic.chains import create_sql_query_chain
    from langchain_core.prompts import PromptTemplate
except ImportError as e:
    print(json.dumps({
        "error": f"langchain not installed: {e}. "
                 "Run: pip install langchain langchain-community langchain-ollama langchain-experimental"
    }))
    sys.exit(1)


def build_prompt(training_data: dict) -> PromptTemplate:
    """
    Build a prompt template that mirrors sqlsage4j's SqlPromptBuilder:
      system = initial_prompt + DDLs + documentation + response_guidelines + few-shot Q&A
    Injects these as static text so both frameworks see identical context.
    """
    parts = []

    parts.append(
        "You are a SQLite expert. Please help to generate a SQL query "
        "to answer the question. Your response should ONLY be based on the given "
        "context and follow the response guidelines and format instructions."
    )

    ddls = training_data.get("ddls", [])
    if ddls:
        parts.append("\n===Tables ")
        for ddl in ddls:
            parts.append(ddl + "\n")

    parts.append("\n===Schema from database\n{table_info}\n")

    docs = training_data.get("documentation", [])
    if docs:
        parts.append("\n===Additional Context \n")
        for doc in docs:
            parts.append(doc + "\n")

    parts.append("""
===Response Guidelines
1. If the provided context is sufficient, please generate a valid SQL query without any explanations for the question.
2. If the provided context is almost sufficient but requires knowledge of a specific string in a particular column, please generate an intermediate SQL query to find the distinct strings in that column. Prepend the query with a comment saying intermediate_sql
3. If the provided context is insufficient, please explain why it can't be generated.
4. Please use the most relevant table(s).
5. If the question has been asked and answered before, please repeat the answer exactly as it was given before.
6. Ensure that the output SQL is SQLite-compliant and executable, and free of syntax errors.
7. If the question asks for destructive operations (DELETE, DROP, UPDATE, INSERT, TRUNCATE, ALTER), REFUSE and explain that only SELECT queries are supported.
8. If the question is vague or overly broad (e.g., "show me the data", "show me everything"), REFUSE and ask for clarification about which specific data they need.
9. If the question is completely unrelated to the database (e.g., weather, general knowledge), explain that you can only answer questions about the data in the provided tables.
10. If the question asks for sensitive data that does not exist in the schema (e.g., passwords, SSNs, tokens), explain that no such data exists in the database.
""")

    qa_pairs = training_data.get("question_answers", [])
    if qa_pairs:
        parts.append("\n===Example Questions and SQL")
        for qa in qa_pairs:
            parts.append(f"Question: {qa['question']}")
            parts.append(f"SQL: {qa['sql']}\n")

    parts.append("""
Limit results to at most {top_k} rows unless the question asks for a specific number.

Use the following format:

Question: Question here
SQLQuery: SQL Query to run

Only output the SQL query, nothing else.

Question: {input}
SQLQuery: """)

    template = "\n".join(parts)
    return PromptTemplate(
        input_variables=["input", "table_info", "top_k"],
        template=template,
    )


def extract_sql(response: str) -> str | None:
    """Extract SQL from LangChain response which may include extra text."""
    if not response:
        return None

    response = response.strip()

    if response.upper().startswith(("SELECT", "WITH", "INSERT", "UPDATE", "DELETE", "DROP")):
        end = response.find(";")
        if end > 0:
            return response[: end + 1]
        return response

    match = re.search(
        r"(?:SQLQuery:\s*)(.*?)(?:\n|$)",
        response,
        re.IGNORECASE | re.DOTALL,
    )
    if match:
        sql = match.group(1).strip()
        if sql:
            return sql

    match = re.search(
        r"```(?:sql)?\s*\n?(.*?)\n?\s*```",
        response,
        re.IGNORECASE | re.DOTALL,
    )
    if match:
        return match.group(1).strip()

    return response


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="gpt-4o")
    parser.add_argument("--temperature", type=float, default=0.0)
    parser.add_argument("--max-tokens", type=int, default=4096)
    parser.add_argument("--database", required=True)
    parser.add_argument("--training-data", required=True)
    args = parser.parse_args()

    base_url = os.environ.get("OPENAI_BASE_URL", "http://localhost:11434")

    with open(args.training_data) as f:
        training = json.load(f)

    llm = ChatOllama(
        model=args.model,
        base_url=base_url,
        temperature=args.temperature,
        num_ctx=8192,
    )

    db = SQLDatabase.from_uri(f"sqlite:///{args.database}")

    prompt = build_prompt(training)
    chain = create_sql_query_chain(llm, db, prompt=prompt, k=50)

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
                response = chain.invoke({"question": question})
                elapsed_ms = int((time.time() - start) * 1000)
                sql = extract_sql(response)
                if sql:
                    print(json.dumps({"sql": sql, "latency_ms": elapsed_ms}), flush=True)
                else:
                    print(json.dumps({"error": f"No SQL extracted from: {response[:200]}", "latency_ms": elapsed_ms}), flush=True)
            except Exception as e:
                elapsed_ms = int((time.time() - start) * 1000)
                print(json.dumps({"error": str(e)[:500], "latency_ms": elapsed_ms}), flush=True)
        else:
            print(json.dumps({"error": f"unknown action: {action}"}), flush=True)


if __name__ == "__main__":
    main()

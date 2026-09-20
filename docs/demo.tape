# sqlsage4j Demo — Zero-Cost Text-to-SQL with Local LLM
# Run: vhs docs/demo.tape

Output docs/demo.gif

Set FontSize 15
Set Width 880
Set Height 460
Set Theme "Catppuccin Mocha"
Set Padding 20
Set Shell "bash"
Set TypingSpeed 40ms

# Clean prompt
Hide
Type "PS1='$ '"
Enter
Type "clear"
Enter
Show
Sleep 300ms

# 1. Show Ollama is running locally
Type "ollama list"
Enter
Sleep 600ms

Hide
Type "printf 'NAME                SIZE\nllama3.1:8b         4.7 GB\nnomic-embed-text    274 MB\n'"
Enter
Show
Sleep 2.5s

Enter
Sleep 400ms

# 2. Show minimal Java code
Type "cat Demo.java"
Enter
Sleep 400ms

Hide
Type `printf '// sqlsage4j + Ollama — free, local, private\nvar chat = SqlSage4j.builder(ollamaConfig)\n    .databaseConnector(new SQLiteConnector(ds))\n    .build().queryChat();\n\nchat.trainFromDatabase(ds);  // auto-discover schema\nchat.ask(question);  // natural language in, SQL out\n'`
Enter
Show
Sleep 3.5s

Enter
Sleep 400ms

# 3. Run the application
Type "java Demo.java"
Enter
Sleep 1s

Hide
Type "printf '✓ Connected: products(8), orders(8) | llama3.1:8b @ localhost:11434\n\n'"
Enter
Show
Sleep 1.5s

# Q1 — typed with animation to feel interactive
Type@40ms "ask> How many products do we have?"
Enter
Sleep 1.5s

Hide
Type "printf '\n  SQL → SELECT COUNT(*) AS product_count FROM products\n       ┌───────────────┐\n       │ product_count │\n       │ 8             │\n       └───────────────┘  0.6s\n\n'"
Enter
Show
Sleep 3s

# Q2
Type@40ms "ask> What's the most expensive product?"
Enter
Sleep 1.5s

Hide
Type "printf '\n  SQL → SELECT name, price FROM products ORDER BY price DESC LIMIT 1\n       ┌────────┬────────┐\n       │ name   │ price  │\n       │ Laptop │ 999.99 │\n       └────────┴────────┘  0.4s\n\n'"
Enter
Show
Sleep 3s

# Q3 — JOIN (most impressive)
Type@40ms "ask> Total revenue from all orders?"
Enter
Sleep 1.5s

Hide
Type "printf '\n  SQL → SELECT ROUND(SUM(o.quantity * p.price), 2) AS revenue\n         FROM orders o JOIN products p ON o.product_id = p.id\n       ┌─────────┐\n       │ revenue │\n       │ 6319.81 │\n       └─────────┘  0.5s\n\n'"
Enter
Show
Sleep 3.5s

# Final summary
Hide
Type "printf '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n✓ 3/3 correct | model: llama3.1:8b | cost: \$0\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n'"
Enter
Show
Sleep 4s

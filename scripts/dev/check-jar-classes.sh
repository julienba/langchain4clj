#!/bin/bash

echo "=== Checking LangChain4j 1.0.0 Package Structure ==="
echo ""

JAR=~/.m2/repository/dev/langchain4j/langchain4j-core/1.0.0/langchain4j-core-1.0.0.jar

echo "1. Message classes (data.message vs model.*):"
jar tf "$JAR" | grep -i "message" | grep -v "Meta-INF" | sort

echo ""
echo "2. Looking for specific classes we use:"
echo ""
echo "UserMessage:"
jar tf "$JAR" | grep "UserMessage.class"
echo ""
echo "SystemMessage:"
jar tf "$JAR" | grep "SystemMessage.class"
echo ""
echo "AiMessage (or AssistantMessage):"
jar tf "$JAR" | grep -E "(AiMessage|AssistantMessage).class"
echo ""
echo "ChatMessage:"
jar tf "$JAR" | grep "ChatMessage.class"
echo ""
echo "ToolExecutionResultMessage:"
jar tf "$JAR" | grep "ToolExecutionResultMessage.class"

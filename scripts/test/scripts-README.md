# Test Scripts - Quick Reference

## 🎯 Qual Script Usar?

### Para Desenvolvimento Diário
```bash
./quick-test.sh
```
- ✅ Executa testes
- ✅ Salva output automaticamente
- ✅ Mostra análise (se tiver Babashka)
- ✅ **Mais simples e rápido**

### Para Controle Total
```bash
./run-tests.sh --save
```
- ✅ Mais opções (--quiet, --verbose)
- ✅ Controle fino sobre output
- ✅ Melhor para CI/CD

### Para Análise Manual
```bash
clojure -M:dev:test 2>&1 | tee test-results/manual.txt
bb analyze-test-results.clj test-results/manual.txt
```

---

## 📋 Scripts Disponíveis

### 1. `quick-test.sh` ⭐ Recomendado

**O que faz:**
1. Roda testes
2. Salva output em `test-results/`
3. Mostra summary
4. Roda análise automática (se tiver Babashka)

**Como usar:**
```bash
# Tornar executável
chmod +x quick-test.sh

# Executar
./quick-test.sh
```

**Output:**
```
🧪 Running tests...

Testing nandoolle.langchain4clj.core-test
...
Ran 45 tests containing 123 assertions.
3 failures, 2 errors.

📊 Test output saved to: test-results/test-run-20250109_120000.txt

Summary:
Ran 45 tests containing 123 assertions.
3 failures, 2 errors.

🔍 Running detailed analysis...

========================================
Test Results Analysis
========================================
...
```

---

### 2. `run-tests.sh`

**O que faz:**
Script completo com múltiplas opções.

**Opções:**
- `--save` - Salva output em arquivos
- `--verbose` - Output detalhado
- `--quiet` - Apenas summary
- `--help` - Ajuda

**Como usar:**
```bash
chmod +x run-tests.sh

# Normal
./run-tests.sh

# Com save
./run-tests.sh --save

# Silencioso
./run-tests.sh --quiet --save
```

---

### 3. `analyze-test-results.clj`

**O que faz:**
Analisa arquivo de output e gera relatório formatado.

**Requer:** Babashka (`brew install borkdude/brew/babashka`)

**Como usar:**
```bash
bb analyze-test-results.clj test-results/test-run-TIMESTAMP.txt
```

**Output:**
```
========================================
Test Results Analysis
========================================

📊 Summary:
  Tests: 45
  Assertions: 123

📈 Statistics:
  Failures: 3
  Errors: 2

❌ Total Issues: 5

🔴 Failures:
  - test-name in namespace

⚠️  Errors:
  - test-name in namespace
========================================
```

---

## 🚀 Workflows Comuns

### Workflow 1: Desenvolvimento Rápido
```bash
# Fazer mudanças
vim src/nandoolle/langchain4clj/core.clj

# Testar
./quick-test.sh
```

### Workflow 2: Debug de Erro
```bash
# 1. Rodar testes
./quick-test.sh

# 2. Se falhar, ver detalhes
cat test-results/test-run-*.txt | grep -A 20 "FAIL in"

# 3. Executar teste específico
clojure -M:dev:test -v namespace/test-name
```

### Workflow 3: CI/CD
```bash
# Em pipeline
./run-tests.sh --save --quiet
EXIT_CODE=$?

if [ $EXIT_CODE -ne 0 ]; then
  echo "Tests failed!"
  cat test-results/test-summary-*.txt
  exit 1
fi
```

---

## 📁 Arquivos Gerados

Todos salvos em `test-results/` (ignorado pelo git):

```
test-results/
├── test-run-20250109_120000.txt      # Output completo
├── test-summary-20250109_120000.txt  # Apenas summary
└── ... (outros runs)
```

**Limpeza:**
```bash
# Limpar tudo
rm -rf test-results/

# Manter apenas últimos 5
ls -t test-results/test-run-*.txt | tail -n +6 | xargs rm
```

---

## 🔧 Setup Inicial

```bash
# 1. Tornar scripts executáveis
chmod +x quick-test.sh run-tests.sh

# 2. (Opcional) Instalar Babashka para análise
brew install borkdude/brew/babashka

# 3. Testar
./quick-test.sh
```

---

## ❓ FAQ

### Qual a diferença entre quick-test.sh e run-tests.sh?

- **quick-test.sh**: Simples, faz tudo automaticamente, melhor para dev
- **run-tests.sh**: Mais opções, melhor para CI/CD e controle fino

### Preciso do Babashka?

**Não!** Os scripts funcionam sem Babashka, mas você perde a análise formatada.

**Sem Babashka:**
```
Summary:
Ran 45 tests
3 failures, 2 errors
```

**Com Babashka:**
```
📊 Summary:
  Tests: 45
  Assertions: 123
🔴 Failures:
  - test-name in namespace
```

### Os arquivos test-results/ vão pro git?

**Não!** Estão no `.gitignore`:
```gitignore
# Test outputs and logs
/test-results/
test-run-*.txt
test-summary-*.txt
```

### Como executar apenas alguns testes?

Use `clojure` diretamente:
```bash
# Namespace específico
clojure -M:dev:test -n nandoolle.langchain4clj.core-test

# Teste específico
clojure -M:dev:test -v nandoolle.langchain4clj.core-test/test-chat
```

---

## 📚 Documentação Completa

Ver `TESTING_GUIDE.md` para guia completo.

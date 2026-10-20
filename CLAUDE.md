## graphify

The iOS app has its own knowledge graph at `ios/graphify-out/` (Swift: Real, RealShare, Shared, tests).

Rules:
- Before any grep or source read for an iOS code question, run from `ios/`: `graphify query "<question>" --budget 800`, `graphify explain "<Symbol>"` or `graphify path "<A>" "<B>"`. Read only the line ranges the graph points to.
- If the graph has no answer, do one targeted grep, not a folder sweep.
- After changing Swift code, run `graphify update .` in `ios/` (AST only, free).
- `ios/graphify-out/` is gitignored and stays local.

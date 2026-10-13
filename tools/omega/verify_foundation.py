"""Read/write/backup/restore proof on the actual local foundation, preserving sources."""
import json
from pathlib import Path
import sqlite3
import subprocess
import sys
import uuid
from contextlib import closing
from brain import Brain, decode_note, digest, encode_note, now

repo = Path(__file__).resolve().parents[2]
brain = Brain(repo)
try:
    initial = brain.index()
    assert not initial["errors"], initial["errors"]
    # Read persisted notes in a fresh OS process, not a shared Python object.
    result = subprocess.run([sys.executable, str(repo/"tools/omega/brain.py"), "--repo", str(repo), "search", "financeiro"],
                            capture_output=True, text=True, encoding="utf-8", check=True,
                            env={**__import__('os').environ, "PYTHONIOENCODING": "utf-8"})
    hits = json.loads(result.stdout)
    assert hits and all(hit["sources"] for hit in hits)
    new_context = brain.context("Continuar desenvolvimento Veyra financeiro", "STANDARD")
    assert new_context["matches"] > 0
    backup = brain.backup()
    source = brain.runtime/backup["path"]
    target = brain.runtime/("verified-restore-"+backup["id"])
    restore = brain.restore(source, target)
    manifest = Brain.verify_backup(source)
    for path, hashed in manifest["files"].items():
        assert digest((target/path).read_bytes()) == hashed, path
    with closing(sqlite3.connect(target/"metadata.sqlite3")) as db:
        assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok"
        assert db.execute("SELECT count(*) FROM documents").fetchone()[0] == initial["indexed"]
    # Two sequential scans with no note changes produce no indexing events/changes.
    brain.index()
    assert brain.index()["changed"] == 0
    body = ("# Fases 0 e 1 — evidência operacional\n\n"
            f"Verificado UTC: {now()}. {initial['indexed']} notas indexadas antes deste registro, sem erros. "
            "Busca em novo processo recuperou fontes reais. Contexto STANDARD gerado. "
            f"Backup {backup['id']} restaurado em cópia isolada; {restore['files']} arquivos comparados por SHA-256 e SQLite íntegro.\n\n"
            "Testes de unidade: consulte docs/omega/VALIDATION.md; não inferir resultados pelo número de notas. "
            "Próxima ação: revisar a fundação e implementar fase 2 sob autorização. "
            "Ponte ChatGPT, embeddings, sync incremental GitHub e execução de agentes pendentes. "
            "A aplicação Obsidian precisa abrir a pasta como Vault; não foi automatizado o cadastro na configuração global.")
    path = "09-SESSION-INTELLIGENCE/SESSION-HANDOFFS/PHASE-1-HANDOFF.md"
    existing = brain.vault/path
    meta = {"id": str(uuid.uuid5(uuid.NAMESPACE_URL, "veyra-omega:"+path)), "title": "Phase 1 Handoff",
            "memory_type": "episodic", "verification": "test_result", "created_at": now(), "updated_at": now(),
            "sources": ["repo:tools/omega/verify_foundation.py", "repo:docs/omega/VALIDATION.md", "backup:"+backup["id"]]}
    if existing.exists():
        # Treat this as a new episode, preserving the prior evidence instead of overwriting.
        path = "09-SESSION-INTELLIGENCE/SESSION-HANDOFFS/PHASE-1-HANDOFF-"+uuid.uuid4().hex[:8]+".md"
        meta["id"] = str(uuid.uuid4())
    brain.write(path, encode_note(meta, body))
    brain.index()
    brain.dashboard()
    final = brain.index()
    report = {"verified_at": now(), "indexed": final["indexed"], "errors": final["errors"],
              "new_process_search_hits": len(hits), "context": new_context, "backup": backup,
              "restore": restore, "all_restored_hashes_match": True, "handoff": path}
    (brain.runtime/"validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
finally:
    brain.close()

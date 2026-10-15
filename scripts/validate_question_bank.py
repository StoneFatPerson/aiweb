import json
import collections
import os

ALLOWED_TYPES = {"SINGLE_CHOICE", "MULTI_CHOICE", "FILL_BLANK", "CALCULATION", "PROOF"}
ALLOWED_SOURCES = {"ORIGINAL_SEED", "AI_GENERATED", "USER_IMPORT", "PUBLIC_OFFICIAL"}
REQUIRED = ["code", "chapterCode", "pointCodes", "type", "difficulty",
            "stem", "options", "answer", "explanation", "keywords"]


def main():
    base = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    path = os.path.join(base, "src", "main", "resources", "data")
    files = sorted(f for f in os.listdir(path) if f.endswith(".json") and "-seed-" in f)

    if not files:
        print("未找到题库文件")
        return

    for name in files:
        print("=" * 46)
        print("文件:", name)
        full = os.path.join(path, name)
        try:
            with open(full, encoding="utf-8") as fp:
                data = json.load(fp)
        except Exception as exc:
            print("  JSON 解析失败:", exc)
            continue

        chapters = data.get("chapters", [])
        questions = data.get("questions", [])

        all_points = set()
        for c in chapters:
            for p in c.get("points", []):
                all_points.add(p.get("code"))

        all_chapters = set(c.get("code") for c in chapters)

        print("  章节数:", len(all_chapters))
        print("  知识点数:", len(all_points))
        print("  题目数:", len(questions))

        counter = collections.Counter(q.get("chapterCode") for q in questions)
        print("  章节分布:")
        for code in sorted(all_chapters):
            print("    ", code, "->", counter.get(code, 0), "题")

        print("  难度分布:", dict(sorted(collections.Counter(q.get("difficulty") for q in questions).items())))
        print("  题型分布:", dict(collections.Counter(q.get("type") for q in questions)))

        codes = [q.get("code") for q in questions]
        dups = sorted(set(c for c in codes if codes.count(c) > 1))
        print("  重复题号:", dups if dups else "无")

        missing_all = [q.get("code") for q in questions
                       if not all(k in q for k in REQUIRED)]
        print("  缺字段:", missing_all if missing_all else "无")

        bad_type = [q.get("code") for q in questions if q.get("type") not in ALLOWED_TYPES]
        print("  题型非法:", bad_type if bad_type else "无")

        bad_point = []
        for q in questions:
            for pc in q.get("pointCodes", []):
                if pc not in all_points:
                    bad_point.append(q.get("code") + " -> " + str(pc))
        print("  知识点引用异常:", sorted(set(bad_point)) if bad_point else "无")


if __name__ == "__main__":
    main()

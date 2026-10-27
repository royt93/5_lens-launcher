# SEARCH-018 — CJK fuzzy gate and pinyin matching (design)

Date: 2026-10-27. Story: `doc/task/todo/p3-search-search-018-cjk-fuzzy-and-pinyin-initials.md`. Device: TECNO KJ7 `115333744A005844` only.

## Evidence check

- The story's example (`微信读书` vs `微信支付`, distance 2) does **not** match: a 4-character token only allows 1 edit, so it returns nothing. Real junk still exists at 1 edit: query `微信读书` returns `微信支书`; query `微信读` returns `微信书`.
- Word-prefix tier never fires for labels without spaces (CJK): confirmed.
- `android.icu.text.Transliterator("Han-Latin; Latin-ASCII")` works under Robolectric sdk 28: `微信读书` -> `wei xin du shu`. **Correction found by lint:** `android.icu.text.Transliterator` exists only from API 29 (not 24); minSdk is 25, so API 25-28 devices get no pinyin matching (the provider returns empty there). Robolectric ICU tests therefore run at sdk 29.

## Decisions (owner, 2026-10-27)

1. Fuzzy is switched off when the label or the token contains a CJK character (Han, Hiragana, Katakana, Hangul).
2. Pinyin data comes from `android.icu` Han-Latin. No dependency, no hand-written table.
3. Pinyin matching is always on: no setting, no new string, no backup-rule change.

## Design

### Part 1 — fuzzy gate

`AppSearchEngine.hasFuzzyMatch` returns false when `token` or `label` has a CJK character. Latin behaviour is unchanged. A named `containsCjk(text)` helper uses `Character.UnicodeScript` (HAN, HIRAGANA, KATAKANA, HANGUL).

### Part 2 — pinyin

- `PinyinProvider` interface: `fun syllables(label: String): List<String>` (empty when none). Default `IcuPinyinProvider` runs only for labels that contain Han, caches per label, and returns empty on any ICU error.
- In `AppSearchEngine.search`, after the label/alias tiers and before fuzzy, a token made of ASCII letters matches a Han label when it is a prefix of the initials string (`wx`, `wxds` for `微信读书`) or a prefix of the concatenated syllables (`weixin`, `weixind`). Score `PINYIN_SCORE = 35` (between "label contains" 30 and "package alias" 40).
- `search` gets an optional `pinyin: PinyinProvider = IcuPinyinProvider` parameter so unit tests inject a fake.
- Kana/romaji is out of scope: ICU reads kanji with Chinese readings, which would be wrong for Japanese. Recorded as a limitation.
- T9 unchanged (package aliases from SEARCH-012 remain its path).

## Tests

- Pure JVM with fake provider: initials, full syllables, prefix only, non-letter tokens ignored, provider failure, no effect on non-Han labels, ranking at 35.
- Robolectric with real ICU: `微信读书`, `支付宝`, `网易云音乐`.
- Fuzzy gate with real CJK labels (Chinese, Japanese, Korean) plus Latin regression.
- Widget on KJ7: type `wx` in the real Home search, `微信` listed.
- Mutation check per branch (`--rerun`).

## Limitations to record

Japanese readings and romaji are not matched; heteronyms (多音字) use ICU's single reading; first ICU call is slow so results are cached per label.

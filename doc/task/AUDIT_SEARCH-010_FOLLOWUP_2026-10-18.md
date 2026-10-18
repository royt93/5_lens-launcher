# Audit follow-up SEARCH-010 UI cấu hình

Ngày 2026-10-18; chỉ OPPO CPH1989 `FUJZIFIR7DQCNRWW`.

## Kết quả

- Hai lỗi layout thật được tái hiện RED rồi sửa: font 1.5 clip nhãn; landscape keypad tràn và làm kết quả cao 0px.
- Full JVM 921/921; lint 0 lỗi / 38 cảnh báo; 10 widget/integration pass; `git diff --check` sạch; staged secret scan chạy trước commit.
- Smoke screenshot portrait/landscape/dark, không thấy quảng cáo; rotation và night mode khôi phục; T9 tắt qua UI.

## Điểm

| Hạng mục | Điểm |
|---|---:|
| Root cause và sửa tối thiểu | 2.8/3 |
| Test/regression | 2.7/3 |
| Smoke và an toàn state | 1.8/2 |
| Tài liệu và giới hạn | 1.8/2 |
| **Tổng** | **9.1/10** |

## Giới hạn

- Không smoke landscape dark, font thật 1.5/2.0 hoặc nhiều kích thước thiết bị.
- Giới hạn 40% là lựa chọn dựa trên viewport OPPO; thiết bị rất thấp vẫn cuộn nhưng chưa đo thêm.
- T9 key prefs hiện `false`, không phải key vắng như trước test.
- Lint vẫn có 38 cảnh báo, chưa rà từng warning cũ.

## Bổ sung Samsung — 2026-10-18

- Samsung A11 SM_A115F `R9JN61LDLFJ` được owner khóa thay BG6. Device smoke font thật 1.5/2.0 portrait + dark landscape đã khép các giới hạn tương ứng ở trên; không quảng cáo. Không claim landscape+font 2.0.
- Hai regression mới fail đúng: nhãn 2 dòng thành 3 ở font 2.0; Material SearchView autoShowKeyboard còn true khi T9 bật. Native API + padding ngang 0 khắc phục, không giảm font.
- 10 targeted pass, 921/921 JVM, lint 0 lỗi/38 warnings, build và diff check pass. Smoke state khôi phục từ snapshot; font/rotation/night system đã xác minh.
- Điểm bổ sung: correctness 2.9/3, coverage 2.8/3, smoke/state 1.8/2, docs 1.8/2: **9.3/10**. Staged secret scan là gate trước commit.

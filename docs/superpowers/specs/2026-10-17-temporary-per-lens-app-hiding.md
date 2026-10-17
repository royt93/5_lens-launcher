# FEAT-011 — Tạm ẩn app riêng theo lens

## Quyết định owner

Scope chọn ngày 2026-10-16; thực thi ngày 2026-10-17. Tạm ẩn không phải tính năng bảo mật: app vẫn thấy trong tìm kiếm, Apps tab và list mode.

- `temporarilyHiddenAppIds` lưu theo component identifier và lens, độc lập membership `ALL`/`SELECTED`, global visibility, favorites, layout và open count.
- Lưới áp dụng membership trước rồi trừ hidden IDs; đổi scope hoặc remove/re-add app không xóa hidden IDs.
- Menu app trên lưới và TalkBack có action ẩn. Lens menu mở danh sách riêng icon/tên/checkbox; chọn nhiều app để khôi phục, Cancel không lưu draft. Khôi phục không tự thêm app vào SELECTED.
- Cho phép ẩn app cuối. CTA khôi phục hiện nếu membership có app nhưng hidden filter làm lưới rỗng; không hiện cho scope rỗng chỉ gồm orphan hoặc app đã ẩn toàn cục. CTA hoạt động trong Clean mode, không che search/list mode hoặc lens khác.
- Hidden IDs của app đã gỡ được giữ. Cài lại cùng component vẫn ẩn. Danh sách restore chỉ liệt kê app hiện cài; restore ngoài membership không làm đổi membership.
- SharedPreferences không fallback state từ lens default sang lens khác. Duplicate lens copy set; delete lens xóa key kể cả default. Không đổi Room schema.
- Backup v2 có field additive `temporarilyHiddenAppIds`: thiếu field mặc định empty; null/sai kiểu/blank/duplicate bị từ chối. Giữ orphan IDs trong export/import. Tham gia workspace preferences snapshot, replacement và rollback. V1 import không đổi hidden state hiện hữu.

## Verification

Unit policy/settings/JSON và snapshot/import-plan test RED trước, GREEN sau. Widget test drive menu production, restore/Cancel, hidden-last-app Clean mode + recreate, search không lọc và TalkBack. Integration file export/import thật kiểm orphan, legacy v2, v1 và failure-after-preferences-write rollback.

Device khóa hiện tại: TECNO BG6 `118743744X002560`, không dùng device khác. Không xóa app data/đổi launcher mặc định, không auto-accept consent. Nếu screenshot có ad, dừng và chờ owner `done`.

Không commit hoặc push nếu owner chưa yêu cầu.

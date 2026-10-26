# Music Tournament

Website bình chọn bài hát theo thể thức loại trực tiếp.

## Công nghệ
- Frontend: Vue 3 + Vite
- Backend: Java 17 + Spring Boot 3.5.7 + Spring Data JPA
- Database mặc định: H2 file (dữ liệu được lưu trong `backend/data`)
- Có cấu hình tùy chọn SQL Server

## Chức năng
- Tạo giải đấu với số bài hát tùy chỉnh (mỗi dòng: `Tên bài — Nghệ sĩ`)
- Xem danh sách giải và sơ đồ các vòng
- Bình chọn một lần cho mỗi cặp trên mỗi trình duyệt (định danh voter lưu trong localStorage)
- Tự đếm phiếu; quản trị viên đóng vòng để chốt người thắng theo tổng phiếu
- Tự chuyển người thắng sang vòng tiếp theo; xử lý số bài không phải lũy thừa của 2
- Quản trị viên có thể mở/đóng vòng và đặt thời gian kết thúc
- Xem kết quả, quán quân
- Đặt tên hiển thị (1–50 ký tự), ghi nhớ trên trình duyệt; nhập lý do vote tùy chọn (tối đa 1.000 ký tự) trước khi xác nhận
- Xem chi tiết mỗi cặp: người vote, bài đã chọn, lý do và thời điểm; 10 phiếu mỗi trang
- Bảng xếp hạng người chơi theo từng giải: tổng lượt vote giảm dần, sau đó số lượt chọn bài thắng ở trận đã chốt; bằng điểm thì cùng hạng
- Chặn bài trùng khi tìm kiếm, thêm hàng loạt, nhập tay, tải file và gọi API: chuẩn hóa tên bài + nghệ sĩ (Unicode, hoa/thường, khoảng trắng), hoặc cùng ID video YouTube / track Spotify
- Trang kết quả có các thẻ chọn giải, lọc theo vòng (64, 32, 16, tứ kết…), trạng thái và phân trang 10/20/50 trận
- Giao diện tối đồng bộ màu nền/chữ/bảng/biểu mẫu; chỉ một nút đóng/mở sidebar ở thanh đầu trang
- Trận chung kết duy nhất được căn giữa, có tiêu đề và hiệu ứng viền riêng
- Sân khấu vinh danh quán quân dùng đĩa nhạc, ảnh bài hát, chữ công bố và pháo giấy tự chạy; có thể tung lại hiệu ứng
- Trang Vòng đấu đặt sân khấu **Anime Final Boss — GRAND CHAMPION** ngay dưới tiến độ, trước điều khiển vòng. Artwork, hào quang tím–xanh và viền vàng đi cùng thông điệp **THE ONE WHO CONQUERED IT ALL**
- **ROAD TO THE THRONE** lấy đối thủ, tên vòng và tỷ số từ bracket thật. Thống kê đếm đối thủ thực sự bị đánh bại, vòng đã đi tiếp và số bài đăng ký; vé vào thẳng và wildcard được thể hiện riêng
- Dùng chung huy hiệu **WINNER / FINALIST / ♛ GRAND CHAMPION** cho cặp đấu, sơ đồ cây, danh sách bài, bảng xếp hạng, Kết quả và trình nghe. Bài dẫn đầu khi giải còn diễn ra chỉ có thứ hạng tạm thời
- Đăng quang tự chạy một lần cho mỗi kết quả trong phiên trình duyệt, không lặp khi polling/đổi trang/tải lại. Khi nhận dữ liệu reset, giao diện xóa quán quân, hành trình và ghi nhớ đăng quang cũ; khi có kết quả mới có thể đăng quang lại. Giải đã đóng vẫn được kiểm tra cập nhật trên các trang theo dõi
- Tìm đoạn nghe thử khớp chính xác tên bài và nghệ sĩ qua API tìm kiếm sẵn có, phát tối đa 30 giây với nút dừng/phát lại. Nếu trình duyệt chặn tự phát, dùng trình nghe trên sân khấu; nếu thiếu bản nghe thử, dùng trình phát YouTube/Spotify từ link bản thu hoặc mở link bài hát. Đổi trang sẽ dừng nhạc; cập nhật phiếu trực tiếp không làm nhạc khởi động lại. Hiệu ứng tôn trọng tùy chọn giảm chuyển động của thiết bị
- API backend lưu dữ liệu trong database

> Lưu ý chống gian lận: định danh voter phía trình duyệt phù hợp cho bản demo/nhóm nhỏ, không thể đảm bảo một người chỉ vote một lần nếu họ xóa dữ liệu trình duyệt hoặc dùng thiết bị khác. Nếu cần vận hành công khai, nên thêm đăng nhập hoặc mã xác thực.

## 1. Chạy backend bằng IntelliJ IDEA
1. Mở thư mục `backend` bằng IntelliJ IDEA.
2. Cài JDK 17 trở lên.
3. Chờ Maven tải dependency.
4. Chạy `src/main/java/com/example/musictournament/MusicTournamentApplication.java`.
5. Backend mặc định chạy ở `http://localhost:8080`.
6. API kiểm tra: `http://localhost:8080/api/health`
7. H2 console: `http://localhost:8080/h2-console`
   - JDBC URL: `jdbc:h2:file:./data/musicdb`
   - User: `sa`
   - Password: để trống

Nếu chạy từ IntelliJ, thư mục data thường được tạo trong working directory của cấu hình chạy.

## 2. Chạy frontend bằng VS Code
1. Mở thư mục `frontend` bằng VS Code.
2. Cài Node.js LTS.
3. Mở Terminal tại thư mục `frontend` rồi chạy:
   ```bash
   npm install
   npm run dev
   ```
4. Mở địa chỉ Vite in trong terminal, thường là `http://localhost:5173`.

Frontend đã cấu hình proxy `/api` tới backend `http://localhost:8080`.

## 3. Dùng thử
- Nhấn **Tạo giải đấu**, nhập tên giải và mỗi bài hát trên một dòng.
- Cú pháp khuyên dùng: `Tên bài — Nghệ sĩ`. Nếu không có nghệ sĩ, chỉ cần nhập tên bài.
- Mở giải đấu để bình chọn. Mỗi trình duyệt có một voter ID riêng.
- Quản trị viên đóng vòng để chốt người thắng theo tổng phiếu; sau đó người thắng tự chuyển sang vòng tiếp theo.
- Quản trị viên có thể vào khu vực **Quản trị**, dùng mã mặc định `admin-demo-2026` để mở/đóng vòng. Đổi mã trong `backend/src/main/resources/application.properties` trước khi chia sẻ.

## 4. SQL Server (tùy chọn)
Mặc định dùng H2 để bạn chạy ngay mà không cần cài database. Để dùng SQL Server:
1. Tạo database, ví dụ `MusicTournamentDB`.
2. Trong `backend/pom.xml`, bỏ comment dependency `mssql-jdbc` nếu đang comment.
3. Sửa `backend/src/main/resources/application-sqlserver.properties` theo máy của bạn.
4. Chạy backend với profile `sqlserver`:
   - IntelliJ VM options: `-Dspring.profiles.active=sqlserver`
   - hoặc environment variable `SPRING_PROFILES_ACTIVE=sqlserver`
5. Đặt biến môi trường `DB_USERNAME` và `DB_PASSWORD`.

Không dùng `ddl-auto=create` trên dữ liệu thật. Bản demo đang dùng `update` để tạo bảng tự động.

## API chính
- `GET /api/health`
- `GET /api/tournaments`
- `POST /api/tournaments`
- `GET /api/tournaments/{id}`
- `POST /api/tournaments/{id}/matches/{matchId}/vote`
- `POST /api/tournaments/{id}/admin/rounds/{roundIndex}/close`
- `POST /api/tournaments/{id}/admin/rounds/{roundIndex}/open`
- `PUT /api/tournaments/{id}/admin/end-time`
- `GET /api/tournaments/{id}/results`
- `GET /api/tournaments/{id}/matches/{matchId}/votes?page=0&size=10` — chi tiết phiếu, trang bắt đầu từ 0, size 1–100
- `GET /api/tournaments/{id}/players?voterKey=...` — bảng xếp hạng người chơi, `voterKey` tùy chọn để đánh dấu người hiện tại; kết quả không công khai voter key

Body API vote hiện tại:
```json
{"voterKey":"browser-uuid","songId":1,"displayName":"Duy Anh","reason":"Giai điệu hay"}
```
`displayName` bắt buộc; `reason` có thể bỏ qua hoặc để trống. Đổi tên không tạo thêm quyền vote. Tên được lưu tại thời điểm vote; bảng xếp hạng dùng tên trong phiếu mới nhất của người chơi trong giải. Phiếu trước bản cập nhật vẫn được tính và hiển thị “Người chơi cũ” khi chưa có tên. Tên và lý do được hiển thị công khai trong chi tiết cặp đấu.

### Kiểm thử
```bash
cd frontend
npm test
npm run build
cd ../backend
mvn test
```
Kiểm thử backend dùng H2 trong bộ nhớ, độc lập với dữ liệu thật. Khi deploy, cấu hình `ddl-auto=update` hiện có sẽ bổ sung hai cột nullable `display_name` và `reason` vào `votes`, giữ các phiếu cũ. Các giải cũ đã chứa bài trùng cần được admin tạo lại với danh sách đã làm sạch; hệ thống chặn vote cặp có cùng tên bài và nghệ sĩ. Các bản cover cùng tên nhưng khác nghệ sĩ được phép tham gia.

## Ghi chú
Luồng tạo giải đã sửa lỗi hoán đổi nghệ sĩ có thể làm lặp/mất bài và phân biệt ô chờ đối thủ với vé vào thẳng. Bản cập nhật giữ dữ liệu giải đã có; nếu một giải cũ đã có bài lặp/mất trong nhánh, quản trị viên cần tạo lại giải từ danh sách đúng trước khi dùng nhánh đó để ghi nhận thành tích.

Đây là nền tảng chạy local để phát triển tiếp. Muốn nhiều người truy cập từ Internet, bạn cần deploy frontend/backend và database lên hosting, đồng thời thay mã quản trị demo bằng xác thực an toàn.

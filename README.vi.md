# Xiaomi Air Purifier NFC Filter Reset Tool (Android) 🇻🇳

[English Documentation](README.md) | **Tiếng Việt**

Công cụ Android mã nguồn mở hỗ trợ **NFC đọc và ghi tag** đầy đủ tính năng dành cho tất cả các dòng máy lọc không khí Xiaomi, dựa trên nghiên cứu giải mã DRM RFID từ [Unethical Info: Hacking My Air Purifier](https://unethical.info/2024/01/24/hacking-my-air-purifier/).

---

## 📱 Tải Xuống Bản Cài Đặt (APK Release)

Tải file APK mới nhất trực tiếp tại thư mục [`release/`](release/):
👉 **[XiaomiFilterReset-v1.0.0.apk](release/XiaomiFilterReset-v1.0.0.apk)**

Hoặc tải trên tab [GitHub Releases](https://github.com/quanarmy/Xiaomi-Filter-Reset-Tool/releases).

---

## ⚡ Các Tính Năng Đầy Đủ

### 1. Quét & Reset 1 Chạm (One-Tap Scan & Reset)
- **Tự động nhận diện thẻ:** Khi đưa mặt lưng điện thoại lại gần đáy lõi lọc, app tự động bắt sóng NFC-A (ISO 14443-3A / NTAG213 / NTAG215 / NTAG216).
- **Giải mã UID & sinh Password:** Tự động áp dụng thuật toán SHA-1 key derivation để sinh ra mật khẩu 4-byte tương ứng với UID của thẻ.
- **Xác thực PWD_AUTH (Lệnh `0x1B`):** Mở khóa vùng nhớ được bảo vệ của chip.
- **Đọc chi tiết thông số lõi:**
  - `UID` (Serial thẻ)
  - `Mật khẩu PWD`
  - `Phần trăm tuổi thọ còn lại` & thanh tiến trình trực quan
  - `Loại lõi lọc` (HEPA tiêu chuẩn, Khử Formaldehyde, Kháng khuẩn...)
  - `Thời gian đã chạy (Block 08)`
  - `Product ID (Block 05)` & `Factory ID (Block 04)`
- **Reset về 100% ngay lập tức:** Ghi đè `00 00 00 00` vào **Block 08** (Work time counter) và tự động đọc lại (read verify) để xác nhận.
- **Chế độ tự động (Auto-Reset Switch):** Chỉ cần bật công tắc, mỗi lần chạm thẻ sẽ tự động reset ngay mà không cần bấm thêm nút nào.
- **Phản hồi Rung (Haptic Feedback):** Báo rung khi đọc và ghi thành công.

---

### 2. Ghi Tuổi Thọ Tuỳ Chọn (Custom Life / Mileage Setter)
- Không chỉ reset về 100%, bạn có thể chọn thanh trượt (slider) từ **0% đến 100%** (ví dụ 80%, 50%, 35%) hoặc nhập trực tiếp giá trị Hex tùy biến vào Block 08.

---

### 3. Tạo Thẻ Trắng Mới Cho Màng Lọc OEM Thứ 3 (Blank Tag Cloning)
- Khi mua lõi lọc bên ngoài giá rẻ không có gắn chip, bạn chỉ cần mua sticker NFC trắng (NTAG213, NTAG215, hoặc NTAG216) dán vào đáy lọc.
- Chọn loại lõi bạn muốn giả lập:
  - **Lõi Chuẩn / HEPA (00003033)** (Màu xám / đen / xanh dương)
  - **Lõi Formaldehyde (00003031)** (Màu xanh lá)
  - **Lõi Kháng Khuẩn (00004130)** (Màu tím)
- Bấm nút và chạm thẻ: App sẽ ghi chuẩn hóa toàn bộ các Block `04, 05, 06, 07, 08`, tự tính mật khẩu theo UID thẻ trắng và cấu hình khóa bảo vệ `AUTH0 = 04` vào đúng Page cấu hình của NTAG. Máy lọc Xiaomi sẽ nhận diện như thẻ chính hãng mới 100%!

---

### 4. Máy Tính Mật Khẩu & Sinh Lệnh APDU (UID Converter)
Dành cho người dùng muốn sử dụng các công cụ phần cứng khác (Flipper Zero, NFC Tools, Proxmark, Chameleon Ultra):
- Nhập UID bất kỳ (hỗ trợ cả định dạng `04:A0:3C:AA:1E:70:80` hoặc `04A03CAA1E7080`).
- Tự động tính Password 4-byte.
- Nút Copy 1-chạm cho từng định dạng lệnh:
  - **NFC Tools (Android/iOS):** `1B<PWD>,3008,A20800000000`
  - **Flipper Zero CLI:** `nfc apdu 1B<PWD> 3008 A20800000000 3008`
  - **Proxmark3:** `hf 14a raw -c 1B<PWD> ; hf 14a raw -c 3008 ; hf 14a raw -c A20800000000`
  - **Chameleon Ultra:** `hw raw 1B<PWD> ; hw raw 3008 ; hw raw A20800000000`

---

### 5. Quản Lý Thiết Bị & Nhật Ký Thay Đổi (Device History & Audit Trail)
- **Danh Sách Thiết Bị Của Tôi (Saved Devices):**
  - Tự động ghi nhận và quản lý từng lõi lọc / máy lọc đã cấu hình.
  - Cho phép người dùng **đặt tên gợi nhớ** (Ví dụ: "Máy phòng khách", "Máy 4 Pro phòng ngủ", "Lõi Tầng 2...").
  - Thống kê chi tiết: **Số lần đã reset**, phần trăm tuổi thọ hiện tại, thời gian reset lần cuối.
  - **Nút "⚡ Reset Lại Thiết Bị Này" (Targeted Re-reset):** 1-Click kích hoạt chế độ nhắm riêng cho thiết bị đó. Khi đưa thẻ vào, app kiểm tra đúng UID thiết bị sẽ lập tức reset về 100%, chống nhầm lẫn với các lõi lọc khác trong nhà.
- **Nhật Ký Thay Đổi (Audit Trail / Timeline):**
  - Lưu vết toàn bộ lịch sử can thiệp qua thời gian: Trạng thái trước (Before) ➔ Trạng thái sau (After), sự thay đổi % tuổi thọ, ngày giờ chi tiết.
  - Lọc nhật ký theo từng thiết bị riêng biệt.
  - Nút **"Khôi phục trạng thái này" (Rollback/Restore)** để đưa thẻ về đúng trạng thái ở một thời điểm trong quá khứ.

---

### 6. Danh Sách Các Thiết Bị Tương Thích & Hướng Dẫn Vị Trí NFC
- **Các model tương thích đã kiểm tra:**
  - Xiaomi Smart Air Purifier 4 Pro
  - Xiaomi Smart Air Purifier 4
  - Xiaomi Smart Air Purifier 4 Lite (bản có tag)
  - Xiaomi Smart Air Purifier 4 Compact
  - Xiaomi Smart Air Purifier Elite
  - Xiaomi Smart Air Purifier Ultra / Ultra Enhanced
  - Xiaomi Mi Air Purifier 3 / 3H / 3C
  - Xiaomi Mi Air Purifier 2 / 2S / 2H
  - Xiaomi Mi Air Purifier Pro / Pro H
  - Xiaomi Mi Air Purifier MAX
  - Mijia Smart Air Purifier 5 / 5s / 6
  - Smartmi Air Purifier 1
- **Vị trí ăng-ten chip NFC trên lõi lọc:**
  - Đa số dòng máy: Ở **đáy tròn của lõi lọc**.
  - Dòng **Xiaomi Air Purifier Elite**: Ở **đỉnh nhựa trên cùng của lõi lọc**.

---

## 🔬 Giải Thích Thuật Toán Sinh Mật Khẩu (Password Key Gen)

Lõi lọc Xiaomi sử dụng chip NFC Type 2 (NTAG213/215/216) có 7-byte UID. Máy lọc khóa thẻ bằng mật khẩu 4-byte sinh từ UID:

1. Lấy mảng byte của UID (7 bytes), ví dụ: `04 A0 3C AA 1E 70 80`
2. Băm SHA-1 của mảng byte này được chuỗi băm 20 bytes:
   `bc af 80 63 33 cc f7 20 cd 44 1a 16 7f 91 4f be 6e a4 a5 13`
3. Lấy byte đầu tiên `b0 = 0xBC = 188` (thập phân).
4. Tính 4 vị trí chỉ mục (index):
   - Index 1 = `188 % 20 = 8` -> Byte thứ 8 là `CD`
   - Index 2 = `(188 + 5) % 20 = 13` -> Byte thứ 13 là `91`
   - Index 3 = `(188 + 13) % 20 = 1` -> Byte thứ 1 là `AF`
   - Index 4 = `(188 + 17) % 20 = 5` -> Byte thứ 5 là `CC`
5. Ghép lại được Mật khẩu: **`CD91AFCC`**
6. Xác thực bằng lệnh PWD_AUTH: `1BCD91AFCC`
7. Ghi đè Block 08 về 0: `A20800000000`

---

## 📄 Bản Quyền (License)

Dự án phát hành theo giấy phép [MIT License](LICENSE).

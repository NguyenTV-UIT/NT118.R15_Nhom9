/**
 * DANH MỤC LỰA CHỌN - khớp với app Android.
 * DB lưu MÃ (code) tiếng Anh; app hiển thị NHÃN tiếng Việt. Mỗi lựa chọn: [mã, nhãn, nhãn ngắn?]
 *   - label      : nhãn dài, dùng ở màn hình Onboarding, Bộ lọc, Xem hồ sơ người khác
 *   - shortLabel : nhãn ngắn, dùng ở màn Sửa hồ sơ (ProfileEdit) và Xem trước hồ sơ (UserProfile)
 * API nhận MÃ, NHÃN hoặc NHÃN NGẮN (VD "never" / "Không hút thuốc" / "Không bao giờ"), luôn trả về mã.
 * App lấy danh sách đầy đủ qua GET /api/meta/options.
 *
 * Thêm / sửa lựa chọn: chỉ sửa file này (cột DB là VARCHAR nên không cần migration).
 */
const OPTIONS = {
  gender: [
    ['male', 'Nam'],
    ['female', 'Nữ'],
    ['other', 'Phi nhị nguyên'],
  ],
  interestedIn: [
    ['male', 'Nam'],
    ['female', 'Nữ'],
    ['other', 'Người phi nhị nguyên'],
    ['everyone', 'Mọi giới tính'],
  ],
  relationshipGoal: [
    ['serious', 'Mối quan hệ nghiêm túc'],
    ['long_term', 'Tìm hiểu để tiến tới mối quan hệ lâu dài', 'Người yêu lâu dài'],
    ['dating', 'Hẹn hò và tìm hiểu', 'Bạn hẹn hò'],
    ['marriage', 'Kết hôn', 'Bạn đời'],
    ['friendship', 'Kết bạn', 'Bạn bè mới'],
    ['meet_new_people', 'Gặp gỡ và kết nối người mới'],
    ['casual', 'Mối quan hệ không ràng buộc'],
    ['not_sure', 'Chưa xác định, cứ tìm hiểu trước', 'Chưa rõ'],
  ],
  zodiac: [
    ['aries', 'Bạch Dương'],
    ['taurus', 'Kim Ngưu'],
    ['gemini', 'Song Tử'],
    ['cancer', 'Cự Giải'],
    ['leo', 'Sư Tử'],
    ['virgo', 'Xử Nữ'],
    ['libra', 'Thiên Bình'],
    ['scorpio', 'Bọ Cạp'],
    ['sagittarius', 'Nhân Mã'],
    ['capricorn', 'Ma Kết'],
    ['aquarius', 'Bảo Bình'],
    ['pisces', 'Song Ngư'],
  ],
  smoking: [
    ['never', 'Không hút thuốc', 'Không bao giờ'],
    ['social', 'Chỉ hút trong một số dịp'],
    ['sometimes', 'Thỉnh thoảng hút thuốc', 'Thỉnh thoảng'],
    ['regularly', 'Hút thuốc thường xuyên', 'Thường xuyên'],
    ['quitting', 'Đang cai thuốc', 'Đang cai'], // chỉ có ở màn Sửa hồ sơ
  ],
  drinking: [
    ['never', 'Không uống', 'Không bao giờ'],
    ['special_occasions', 'Chỉ uống trong các dịp đặc biệt'],
    ['sometimes', 'Thỉnh thoảng uống', 'Thỉnh thoảng'],
    ['often', 'Uống khá thường xuyên', 'Thường xuyên'],
    ['weekends', 'Uống vào cuối tuần', 'Cuối tuần'], // chỉ có ở màn Sửa hồ sơ
  ],
  exercise: [
    ['daily', 'Tập luyện mỗi ngày', 'Mỗi ngày'], // chỉ có ở màn Sửa hồ sơ
    ['very_active', 'Tập luyện thường xuyên', 'Thường xuyên'],
    ['weekly', 'Tập vài lần mỗi tuần'],
    ['sometimes', 'Thỉnh thoảng tập luyện', 'Thỉnh thoảng'],
    ['rarely', 'Hiếm khi tập luyện'],
    ['never', 'Không tập luyện', 'Không tập'],
  ],
  education: [
    ['high_school_student', 'Đang học trung học phổ thông'],
    ['high_school', 'Tốt nghiệp trung học phổ thông', 'Trung học'],
    ['vocational', 'Trung cấp hoặc cao đẳng nghề'],
    ['college', 'Cao đẳng'],
    ['university_student', 'Đang học đại học'],
    ['bachelor', 'Đại học', 'Đại học'],
    ['postgraduate', 'Sau đại học / Thạc sĩ', 'Thạc sĩ'],
    ['phd', 'Tiến sĩ', 'Tiến sĩ'],
    ['other', 'Khác', 'Khác'], // chỉ có ở màn Sửa hồ sơ
  ],
};

/** Lý do báo cáo - màn ReportUser. */
OPTIONS.reportReason = [
  ['fake_profile', 'Ảnh giả hoặc mạo danh'],
  ['sexual_content', 'Nội dung nhạy cảm, khiêu dâm'],
  ['harassment', 'Quấy rối hoặc bắt nạt'],
  ['scam_spam', 'Lừa đảo hoặc spam'],
  ['underage', 'Có vẻ chưa đủ 18 tuổi'],
  ['other', 'Lý do khác'],
];
/** Lý do rời đi - màn DeleteAccount. */
OPTIONS.deleteReason = [
  ['found_match', 'Mình đã tìm được người phù hợp'],
  ['taking_break', 'Mình muốn nghỉ một thời gian'],
  ['privacy', 'Mình gặp vấn đề về quyền riêng tư'],
  ['other', 'Lý do khác'],
];

/** 20 sở thích trên màn hình Interests của app (thứ tự như app). */
const APP_INTERESTS = [
  'Du lịch', 'Âm nhạc', 'Xem phim và series', 'Đọc sách', 'Nấu ăn',
  'Chơi game', 'Thể thao', 'Tập gym', 'Cà phê', 'Ẩm thực',
  'Nhiếp ảnh', 'Nghệ thuật', 'Công nghệ', 'Thú cưng', 'Thiên nhiên và cắm trại',
  'Thời trang', 'Khiêu vũ', 'Khám phá địa điểm mới', 'Hoạt động ngoài trời', 'Tình nguyện',
];

/** Giới hạn khớp với app (SeekBar / EditText). */
const LIMITS = {
  heightCm: { min: 140, max: 200 },
  weightKg: { min: 35, max: 120 },
  maxDistanceKm: { min: 1, max: 100 },
  age: { min: 18, max: 100 },
  displayName: { min: 2, max: 100 },
  bio: { max: 300 },
  password: { min: 8, max: 72 },
  photos: { min: 2, max: 6 },
  interests: { min: 3, max: 20 },
};

// Chuẩn hoá để so nhãn không phân biệt hoa thường / khoảng trắng thừa
const norm = (s) => String(s).normalize('NFC').trim().replace(/\s+/g, ' ').toLowerCase();

const lookup = {};
for (const [key, list] of Object.entries(OPTIONS)) {
  lookup[key] = new Map();
  for (const [code, label, shortLabel] of list) {
    for (const text of [code, label, shortLabel]) {
      if (!text) continue;
      const k = norm(text);
      // Bắt lỗi khi thêm nhãn trùng nhau trong cùng 1 mục (VD 2 lựa chọn cùng tên "Thỉnh thoảng")
      if (lookup[key].has(k) && lookup[key].get(k) !== code) {
        throw new Error(`options.${key}: nhãn "${text}" bị trùng giữa ${lookup[key].get(k)} và ${code}`);
      }
      lookup[key].set(k, code);
    }
  }
}

/** Đổi mã hoặc nhãn -> mã. Không khớp trả về undefined. */
function toCode(key, value) {
  if (value == null) return undefined;
  return lookup[key].get(norm(value));
}

function codes(key) {
  return OPTIONS[key].map(([code]) => code);
}

function labelOf(key, code, short = false) {
  const found = OPTIONS[key].find(([c]) => c === code);
  if (!found) return null;
  return short ? found[2] || found[1] : found[1];
}

/** Dạng trả cho app: { key: [{ code, label, shortLabel }] } (shortLabel = label nếu màn Sửa hồ sơ không có nhãn riêng) */
function asJson() {
  return Object.fromEntries(
    Object.entries(OPTIONS).map(([key, list]) => [
      key,
      list.map(([code, label, shortLabel]) => ({ code, label, shortLabel: shortLabel || label })),
    ]),
  );
}

module.exports = { OPTIONS, APP_INTERESTS, LIMITS, toCode, codes, labelOf, asJson, norm };

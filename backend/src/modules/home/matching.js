/**
 * THUẬT TOÁN CHẤM ĐIỂM PHÙ HỢP (matchScore 0 - 100)
 *
 * Bước 1 (SQL - home.service.js): LỌC ứng viên hợp lệ
 *   giới tính 2 chiều, độ tuổi, khoảng cách, chưa quẹt, không chặn nhau.
 * Bước 2 (file này): CHẤM ĐIỂM từng ứng viên theo trọng số rồi sắp xếp giảm dần.
 *
 *   Yếu tố                Điểm tối đa   Cách tính
 *   Sở thích chung             35       tỉ lệ sở thích của mình mà người kia cũng có
 *   Khoảng cách                25       càng gần càng cao, giảm tuyến tính tới bán kính tìm kiếm
 *   Mức độ hoạt động           20       online ≤ 1 ngày: 20, ≤ 7 ngày: 12, ≤ 30 ngày: 5
 *   Tuổi gần nhau              10       chênh 0 tuổi: 10, chênh ≥ 10 tuổi: 0
 *   Hồ sơ đầy đủ               10       có ảnh 4, có bio 3, có sở thích 3
 *
 *   + Ưu tiên ẩn: người đã like mình được cộng LIKED_ME_BOOST khi SẮP XẾP
 *     (không cộng vào matchScore trả về, để không lộ ai đã like mình).
 */
const WEIGHTS = {
  interests: 35,
  distance: 25,
  activity: 20,
  age: 10,
  completeness: 10,
};
const LIKED_ME_BOOST = 8;
const DEFAULT_RADIUS_KM = 100; // dùng khi không có bán kính tìm kiếm

const DAY = 24 * 3600 * 1000;

function interestScore(common, myInterestCount) {
  // Mình chưa chọn sở thích -> cho nửa số điểm để không thiệt ai
  if (!myInterestCount) return WEIGHTS.interests / 2;
  return WEIGHTS.interests * Math.min(1, common / Math.min(myInterestCount, 5));
}

function distanceScore(distanceKm, radiusKm) {
  if (distanceKm == null) return WEIGHTS.distance * 0.4; // chưa có vị trí -> điểm trung bình thấp
  const r = radiusKm || DEFAULT_RADIUS_KM;
  return WEIGHTS.distance * Math.max(0, 1 - distanceKm / r);
}

function activityScore(lastActiveAt) {
  if (!lastActiveAt) return 0;
  const ago = Date.now() - new Date(lastActiveAt).getTime();
  if (ago <= DAY) return WEIGHTS.activity;
  if (ago <= 7 * DAY) return WEIGHTS.activity * 0.6;
  if (ago <= 30 * DAY) return WEIGHTS.activity * 0.25;
  return 0;
}

function ageScore(candidateAge, myAge) {
  if (candidateAge == null || myAge == null) return 0;
  return WEIGHTS.age * Math.max(0, 1 - Math.abs(candidateAge - myAge) / 10);
}

function completenessScore({ hasPhoto, hasBio, interestCount }) {
  return (hasPhoto ? 4 : 0) + (hasBio ? 3 : 0) + (interestCount > 0 ? 3 : 0);
}

/**
 * @param c   ứng viên: { distanceKm, commonInterests, lastActiveAt, age, hasPhoto, hasBio, interestCount, likedMe }
 * @param me  người xem: { age, interestCount, radiusKm }
 * @returns { matchScore, rankScore }
 */
function score(c, me) {
  const total =
    interestScore(c.commonInterests, me.interestCount) +
    distanceScore(c.distanceKm, me.radiusKm) +
    activityScore(c.lastActiveAt) +
    ageScore(c.age, me.age) +
    completenessScore(c);

  const matchScore = Math.round(Math.min(100, total));
  return { matchScore, rankScore: total + (c.likedMe ? LIKED_ME_BOOST : 0) };
}

module.exports = { score, WEIGHTS, LIKED_ME_BOOST };

import QRCode from 'qrcode/lib/core/qrcode.js'

// 将支付二维码内容编码为适合小程序 Canvas 绘制的布尔矩阵。
export function createQrMatrix(value) {
  const result = QRCode.create(String(value), { errorCorrectionLevel: 'M' })
  return { size: result.modules.size, data: Array.from(result.modules.data) }
}

export type Product = { id: number; name: string; description: string; priceCents: number; available: number; published: boolean }
export type OrderItem = { productId: number; productName: string; quantity: number; unitPriceCents: number }
export type Order = { id: number; customer: string; items: OrderItem[]; totalCents: number; status: string; address: string; trackingNo: string | null; fulfillmentHold: boolean }
export type CartItem = { id: number; productId: number; quantity: number; selected: boolean }
export type Quote = { productId: number; productName: string; quantity: number; unitPriceCents: number; subtotalCents: number; available: boolean }
export type Payment = { id: string; status: string; amountCents: number }
export type Refund = { id: string; status: string; amountCents: number }
export type PaymentView = { payment: Partial<Payment>; refund: Partial<Refund> }
export type AfterSale = { id: number; orderId: number; reason: string; status: string; type: string; decisionReason: string; returnTrackingNo: string; restocked: boolean }
export type Tracking = { eventId: string; status: string; location: string; occurredAt: string }
export const money = (cents: number) => '¥' + (cents / 100).toFixed(2)
const states: Record<string, string> = {
  PENDING_PAYMENT: '待支付', AWAITING_SHIPMENT: '待发货', SHIPPED: '已发货', COMPLETED: '已完成', CLOSED_UNPAID: '已关闭', CLOSED_REFUNDED: '已退款关闭',
  APPLIED: '待审核', REJECTED: '已拒绝', WAITING_RETURN: '待寄回', RETURN_IN_TRANSIT: '退货运输中', REFUNDING: '退款处理中', REFUNDED: '退款完成',
  INITIATED: '等待付款', SUCCEEDED: '成功', FAILED: '失败', REQUESTED: '等待处理', LATE_PAYMENT: '迟到支付，补偿中',
  PICKED_UP: '已揽收', IN_TRANSIT: '运输中', OUT_FOR_DELIVERY: '派送中', DELIVERED: '已妥投', EXCEPTION: '物流异常', RESUMED: '已恢复', CREATED: '等待揽收'
}
export const label = (status: string) => states[status] || status

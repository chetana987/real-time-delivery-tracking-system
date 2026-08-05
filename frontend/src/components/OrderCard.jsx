import StatusBadge from './StatusBadge';

export default function OrderCard({ order, actions, dim = false }) {
  const total = order.totalAmount != null ? Number(order.totalAmount).toFixed(2) : '—';
  const placedAt = order.createdAt
    ? new Date(order.createdAt).toLocaleString([], {
        day: 'numeric',
        month: 'short',
        hour: '2-digit',
        minute: '2-digit',
      })
    : '';

  return (
    <div
      className={`rounded-2xl border bg-white p-4 shadow-sm transition sm:p-5 ${
        dim ? 'border-cream-100 opacity-75' : 'border-cream-200'
      }`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate text-base font-semibold text-charcoal-900">
            {order.restaurantName || `Order #${order.id}`}
          </h3>
          <p className="mt-0.5 text-sm text-charcoal-600">
            To: {order.deliveryAddress || '—'}
          </p>
        </div>
        <StatusBadge status={order.status} />
      </div>

      <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-cream-100 pt-3 text-sm">
        <span className="text-charcoal-600">
          Order #<span className="font-medium text-charcoal-900">{order.id}</span>
          {placedAt ? <span className="text-charcoal-600"> · {placedAt}</span> : null}
        </span>
        <span className="font-bold text-charcoal-900">${total}</span>
      </div>

      {actions ? <div className="mt-4 flex flex-wrap gap-2">{actions}</div> : null}
    </div>
  );
}

const STATUS_STYLES = {
  PLACED: 'bg-amber-100 text-amber-900 ring-amber-300',
  ACCEPTED: 'bg-orange-100 text-orange-900 ring-orange-300',
  PICKED_UP: 'bg-terra-100 text-terra-800 ring-terra-300',
  OUT_FOR_DELIVERY: 'bg-lime-100 text-lime-900 ring-lime-300',
  DELIVERED: 'bg-emerald-100 text-emerald-800 ring-emerald-300',
  CANCELLED: 'bg-stone-100 text-stone-600 ring-stone-300',
};

const STATUS_LABELS = {
  PLACED: 'Placed',
  ACCEPTED: 'Accepted',
  PICKED_UP: 'Picked up',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

const STATUS_DOTS = {
  PLACED: 'bg-amber-500',
  ACCEPTED: 'bg-orange-500',
  PICKED_UP: 'bg-terra-500',
  OUT_FOR_DELIVERY: 'bg-lime-600',
  DELIVERED: 'bg-emerald-600',
  CANCELLED: 'bg-stone-400',
};

export default function StatusBadge({ status }) {
  const style = STATUS_STYLES[status] || STATUS_STYLES.PLACED;
  const dot = STATUS_DOTS[status] || STATUS_DOTS.PLACED;
  const label = STATUS_LABELS[status] || status;

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${style}`}
    >
      <span className={`h-1.5 w-1.5 rounded-full ${dot}`} />
      {label}
    </span>
  );
}

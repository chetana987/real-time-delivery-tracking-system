import StatusBadge from './StatusBadge';

const STEPS = [
  { key: 'PLACED', label: 'Placed' },
  { key: 'ACCEPTED', label: 'Accepted' },
  { key: 'PICKED_UP', label: 'Picked up' },
  { key: 'OUT_FOR_DELIVERY', label: 'Out for delivery' },
  { key: 'DELIVERED', label: 'Delivered' },
];

export default function StatusProgress({ status }) {
  if (status === 'CANCELLED') {
    return (
      <div className="flex flex-col gap-3">
        <div className="flex items-center gap-3">
          <StatusBadge status="CANCELLED" />
          <p className="text-sm text-charcoal-600">This order was cancelled.</p>
        </div>
        {renderTrack(-1)}
      </div>
    );
  }

  const currentIndex = STEPS.findIndex((s) => s.key === status);

  return (
    <div>
      <div className="mb-4 flex items-center gap-3">
        <StatusBadge status={status} />
        <p className="text-sm text-charcoal-600">
          {currentIndex === STEPS.length - 1
            ? 'Delivered — enjoy!'
            : `Step ${Math.min(currentIndex + 1, STEPS.length)} of ${STEPS.length}`}
        </p>
      </div>
      {renderTrack(currentIndex >= 0 ? currentIndex : -1)}
    </div>
  );
}

function renderTrack(currentIndex) {
  return (
    <ol className="flex w-full items-start">
      {STEPS.map((step, i) => {
        const done = currentIndex >= i;
        const active = currentIndex === i;
        return (
          <li key={step.key} className="relative flex flex-1 flex-col items-center">
            {i < STEPS.length - 1 && (
              <span
                aria-hidden="true"
                className={`absolute left-1/2 top-3 h-1 w-full -translate-y-1/2 rounded ${
                  currentIndex > i ? 'bg-terra-500' : 'bg-cream-200'
                }`}
              />
            )}
            <span
              className={`relative z-10 flex h-6 w-6 items-center justify-center rounded-full border-2 text-[10px] font-bold transition ${
                done
                  ? 'border-terra-500 bg-terra-500 text-white'
                  : active
                    ? 'border-terra-500 bg-cream-50 text-terra-600 ring-4 ring-terra-100'
                    : 'border-cream-200 bg-cream-50 text-charcoal-600'
              }`}
            >
              {i + 1}
            </span>
            <span
              className={`mt-2 max-w-[5.5rem] text-center text-[11px] leading-tight ${
                done ? 'font-semibold text-charcoal-900' : 'text-charcoal-600'
              }`}
            >
              {step.label}
            </span>
          </li>
        );
      })}
    </ol>
  );
}

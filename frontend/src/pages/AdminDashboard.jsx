export default function AdminDashboard() {
  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-charcoal-900">Admin dashboard</h1>
        <p className="mt-1 text-sm text-charcoal-600">You are signed in as an administrator.</p>
      </div>

      <p className="rounded-2xl border border-dashed border-cream-200 bg-white px-5 py-8 text-center text-sm text-charcoal-600">
        This is the administrator area for Chetana Delivery. Restaurant and menu management is
        available through the API; no management console is included in this minimal view.
      </p>
    </div>
  );
}
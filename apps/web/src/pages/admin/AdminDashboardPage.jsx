import { Link } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, LabelList, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Card, ErrorState, PageHeader, Skeleton } from '@/components/ui';
import { useAdminStats } from '@/features/admin/hooks';
import { formatDate } from '@/lib/dates';

/**
 * Chart tokens (dataviz reference palette, validated with validate_palette.js: light mode,
 * adjacent CVD ΔE 24.7, normal-vision ΔE 33.6, both ≥ 3:1 on the surface).
 */
const C = {
  series1: '#2a78d6',
  series2: '#eb6834',
  grid: '#e5e7eb',
  axis: '#4b5563',
  ink: '#111827',
  surface: '#ffffff',
};
const tick = { fill: C.axis, fontSize: 12 };

function StatCard({ label, value, to, tone }) {
  return (
    <Link
      to={to}
      className="rounded-xl border border-gray-200 bg-white p-4 hover:border-brand-700 hover:shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-700"
    >
      <p className="flex items-center gap-1.5 text-sm text-gray-700">
        {tone === 'critical' && <span aria-hidden>⚠️</span>}
        {tone === 'warning' && <span aria-hidden>🚩</span>}
        {label}
      </p>
      <p className={`mt-1 text-3xl font-bold ${tone === 'critical' && value > 0 ? 'text-red-800' : 'text-gray-900'}`}>{value.toLocaleString()}</p>
    </Link>
  );
}

function ChartTooltip({ active, payload, label, title }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm shadow-md">
      <p className="font-medium text-gray-900">{title ? title(String(label)) : label}</p>
      {payload.map((p) => (
        <p key={p.name} className="flex items-center gap-2 text-gray-700">
          <span className="h-2.5 w-2.5 rounded-full" style={{ background: p.color }} aria-hidden />
          {p.name}: <span className="font-semibold text-gray-900">{p.value}</span>
        </p>
      ))}
    </div>
  );
}

function DataTableToggle({ headers, rows }) {
  return (
    <details className="mt-3 text-sm">
      <summary className="cursor-pointer font-medium text-brand-700">Show as table</summary>
      <table className="mt-2 w-full text-left tabular-nums">
        <thead>
          <tr className="text-gray-700">
            {headers.map((h) => (
              <th key={h} scope="col" className="py-1 pr-4 font-semibold">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-t border-gray-100 text-gray-800">
              {r.map((c, j) => (
                <td key={j} className="py-1 pr-4">
                  {c}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}

const weekLabel = (w) => formatDate(w, { day: 'numeric', month: 'short' });

function LoansPerWeek({ data }) {
  const last = data.length - 1;
  const endLabel = (name) =>
    function EndLabel(props) {
      if (props.index !== last) return null;
      return (
        <text x={Number(props.x) + 8} y={Number(props.y) + 4} fontSize={12} fill={C.ink}>
          {name}
        </text>
      );
    };
  return (
    <Card>
      <h2 className="text-lg font-semibold text-gray-900">Loans per week</h2>
      <p className="text-sm text-gray-600">Requests created and how many were approved, last 12 weeks.</p>
      <div className="mt-4 h-64">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 72, left: -16, bottom: 0 }}>
            <CartesianGrid vertical={false} stroke={C.grid} strokeWidth={1} />
            <XAxis dataKey="week" tickFormatter={weekLabel} tick={tick} axisLine={{ stroke: C.grid }} tickLine={false} interval="preserveStartEnd" />
            <YAxis allowDecimals={false} tick={tick} axisLine={false} tickLine={false} />
            <Tooltip content={<ChartTooltip title={(w) => `Week of ${weekLabel(w)}`} />} cursor={{ stroke: C.axis, strokeWidth: 1 }} />
            <Legend verticalAlign="top" align="right" iconType="circle" wrapperStyle={{ fontSize: 12, paddingBottom: 8 }} formatter={(v) => <span style={{ color: C.axis }}>{v}</span>} />
            <Line type="linear" dataKey="requested" name="Requested" stroke={C.series1} strokeWidth={2} dot={false} activeDot={{ r: 5, stroke: C.surface, strokeWidth: 2 }} isAnimationActive={false}>
              <LabelList content={endLabel('Requested')} />
            </Line>
            <Line type="linear" dataKey="approved" name="Approved" stroke={C.series2} strokeWidth={2} dot={false} activeDot={{ r: 5, stroke: C.surface, strokeWidth: 2 }} isAnimationActive={false}>
              <LabelList content={endLabel('Approved')} />
            </Line>
          </LineChart>
        </ResponsiveContainer>
      </div>
      <DataTableToggle headers={['Week of', 'Requested', 'Approved']} rows={data.map((d) => [weekLabel(d.week), d.requested, d.approved])} />
    </Card>
  );
}

function RankedBars({ title, subtitle, data, valueName }) {
  return (
    <Card>
      <h2 className="text-lg font-semibold text-gray-900">{title}</h2>
      <p className="text-sm text-gray-600">{subtitle}</p>
      {data.length === 0 ? (
        <p className="mt-6 text-sm text-gray-600">No loans yet.</p>
      ) : (
        <div className="mt-4" style={{ height: Math.max(120, data.length * 44) }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} layout="vertical" margin={{ top: 0, right: 40, left: 0, bottom: 0 }} barCategoryGap={12}>
              <CartesianGrid horizontal={false} stroke={C.grid} strokeWidth={1} />
              <XAxis type="number" allowDecimals={false} tick={tick} axisLine={false} tickLine={false} />
              <YAxis type="category" dataKey="name" width={120} tick={tick} axisLine={{ stroke: C.grid }} tickLine={false} />
              <Tooltip content={<ChartTooltip />} cursor={{ fill: '#f3f4f6' }} />
              <Bar dataKey="loans" name={valueName} fill={C.series1} barSize={20} radius={[0, 4, 4, 0]} isAnimationActive={false}>
                <LabelList dataKey="loans" position="right" fill={C.ink} fontSize={12} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
      <DataTableToggle headers={['Name', valueName]} rows={data.map((d) => [d.name, d.loans])} />
    </Card>
  );
}

/** Screen 12: stat cards, charts, quick links. */
export default function AdminDashboardPage() {
  const { data, isLoading, isError, error, refetch } = useAdminStats();
  if (isError) return <ErrorState error={error} onRetry={() => refetch()} />;
  return (
    <>
      <PageHeader title="Dashboard" subtitle={data ? `Overdue rate ${(data.overdueRate * 100).toFixed(1)}% (target below 10%)` : undefined} />
      {isLoading || !data ? (
        <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-3">
            <StatCard label="Total users" value={data.cards.totalUsers} to="/admin/users" />
            <StatCard label="Active items" value={data.cards.activeItems} to="/admin/items" />
            <StatCard label="Active loans" value={data.cards.activeLoans} to="/admin/loans?status=ACTIVE" />
            <StatCard label="Overdue loans" value={data.cards.overdueLoans} to="/admin/loans?status=OVERDUE" tone="critical" />
            <StatCard label="Pending requests" value={data.cards.pendingRequests} to="/admin/loans?status=PENDING" />
            <StatCard label="Open reports" value={data.cards.openReports} to="/admin/reports" tone="warning" />
          </div>
          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <div className="lg:col-span-2">
              <LoansPerWeek data={data.charts.loansPerWeek} />
            </div>
            <RankedBars title="Top categories" subtitle="By number of approved or completed loans." data={data.charts.topCategories} valueName="Loans" />
            <RankedBars title="Top lenders" subtitle="By number of items actually lent." data={data.charts.topLenders} valueName="Loans" />
          </div>
        </>
      )}
    </>
  );
}

import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ChartData, ChartOptions } from 'chart.js';
import { ButtonModule } from 'primeng/button';
import { ChartModule } from 'primeng/chart';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ManagerApi } from '../core/manager-api';
import { ClaimAttention, ClaimStatus, ManagerDashboard } from '../core/models';
import {
  ageLabel,
  claimTypeLabel,
  currencySummary,
  dateTime,
  errorMessage,
  money,
  shortDate,
  statusColor,
  statusLabel,
  statusSeverity,
} from '../core/ui';

const STATUS_ORDER: ClaimStatus[] = [
  'UNDER_REVIEW',
  'INFO_REQUESTED',
  'ASSESSED',
  'APPROVED',
];

const CURRENCY_COLORS = ['#1f5fa8', '#0f8a8a', '#d97706', '#7c3aed', '#dc2626', '#0e9f6e'];

@Component({
  selector: 'app-manager',
  imports: [FormsModule, ChartModule, TableModule, TagModule, ButtonModule, SelectModule],
  templateUrl: './manager.page.html',
  styleUrl: './manager.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ManagerPage implements OnInit {
  private readonly api = inject(ManagerApi);

  readonly loading = signal(true);
  readonly dashboard = signal<ManagerDashboard | null>(null);
  readonly error = signal<string | null>(null);
  readonly market = signal('');

  readonly markets = [
    { label: 'All markets', value: '' },
    { label: 'Malaysia', value: 'MY' },
    { label: 'Singapore', value: 'SG' },
    { label: 'Hong Kong', value: 'HK' },
    { label: 'Thailand', value: 'TH' },
    { label: 'Indonesia', value: 'ID' },
    { label: 'Vietnam', value: 'VN' },
  ];

  readonly statusLabel = statusLabel;
  readonly claimTypeLabel = claimTypeLabel;
  readonly statusSeverity = statusSeverity;
  readonly money = money;
  readonly shortDate = shortDate;
  readonly dateTime = dateTime;
  readonly ageLabel = ageLabel;
  readonly currencySummary = currencySummary;

  readonly doughnutOptions: ChartOptions<'doughnut'> = {
    responsive: true,
    maintainAspectRatio: false,
    cutout: '64%',
    plugins: {
      legend: {
        position: 'bottom',
        labels: { usePointStyle: true, boxWidth: 8, padding: 14, color: '#475569' },
      },
    },
  };

  readonly barOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: { usePointStyle: true, boxWidth: 8, padding: 14, color: '#475569' },
      },
    },
    scales: {
      x: { grid: { display: false }, ticks: { color: '#64748b' } },
      y: {
        beginAtZero: true,
        grid: { color: '#e2e8f0' },
        ticks: { color: '#64748b' },
      },
    },
  };

  readonly percentBarOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: { usePointStyle: true, boxWidth: 8, padding: 14, color: '#475569' },
      },
    },
    scales: {
      x: { grid: { display: false }, ticks: { color: '#64748b' } },
      y: {
        beginAtZero: true,
        max: 100,
        grid: { color: '#e2e8f0' },
        ticks: { color: '#64748b', callback: (value) => `${value}%` },
      },
    },
  };

  readonly stackedBarOptions: ChartOptions<'bar'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: true,
        position: 'bottom',
        labels: { usePointStyle: true, boxWidth: 8, padding: 14, color: '#475569' },
      },
    },
    scales: {
      x: { stacked: true, grid: { display: false }, ticks: { color: '#64748b' } },
      y: {
        stacked: true,
        beginAtZero: true,
        grid: { color: '#e2e8f0' },
        ticks: { color: '#64748b', precision: 0 },
      },
    },
  };

  readonly statusChart = computed<ChartData<'doughnut'>>(() => {
    const counts = this.dashboard()?.portfolio.openByStatus ?? ({} as Record<ClaimStatus, number>);
    const entries = (Object.entries(counts) as Array<[ClaimStatus, number]>).filter(
      ([, count]) => count > 0,
    );
    return {
      labels: entries.map(([status]) => statusLabel(status)),
      datasets: [
        {
          data: entries.map(([, count]) => count),
          backgroundColor: entries.map(([status]) => statusColor(status)),
          borderWidth: 0,
        },
      ],
    };
  });

  readonly marketChart = computed<ChartData<'bar'>>(() => {
    const markets = this.dashboard()?.byMarket ?? [];
    return {
      labels: markets.map((market) => market.marketName),
      datasets: [
        {
          label: 'Open claims',
          data: markets.map((market) => market.openClaims),
          backgroundColor: '#1f5fa8',
          borderRadius: 6,
        },
      ],
    };
  });

  readonly reserveChart = computed<ChartData<'bar'>>(() => {
    const markets = this.dashboard()?.byMarket ?? [];
    const currencies = [
      ...new Set(markets.flatMap((market) => Object.keys(market.openReserveByCurrency))),
    ];
    return {
      labels: markets.map((market) => market.marketName),
      datasets: currencies.map((currency, index) => ({
        label: currency,
        data: markets.map((market) => market.openReserveByCurrency[currency] ?? 0),
        backgroundColor: CURRENCY_COLORS[index % CURRENCY_COLORS.length],
        borderRadius: 5,
      })),
    };
  });

  readonly officerChart = computed<ChartData<'bar'>>(() => {
    const officers = this.dashboard()?.officerWorkload ?? [];
    return {
      labels: officers.map((officer) => officer.officerName),
      datasets: STATUS_ORDER.map((status) => ({
        label: statusLabel(status),
        data: officers.map((officer) => officer.byStatus[status] ?? 0),
        backgroundColor: statusColor(status),
        borderRadius: 4,
      })),
    };
  });

  readonly typeChart = computed<ChartData<'doughnut'>>(() => {
    const types = this.dashboard()?.byType ?? [];
    return {
      labels: types.map((type) => claimTypeLabel(type.claimType)),
      datasets: [
        {
          data: types.map((type) => type.openClaims),
          backgroundColor: ['#1f5fa8', '#0f8a8a'],
          borderWidth: 0,
        },
      ],
    };
  });

  readonly attentionBlocks = computed(() => {
    const attention = this.dashboard()?.attention;
    return [
      {
        title: 'Oldest unassigned',
        icon: 'pi pi-inbox',
        tone: 'warn',
        items: attention?.oldestUnassigned ?? [],
      },
      {
        title: 'Waiting on claimant',
        icon: 'pi pi-question-circle',
        tone: 'warn',
        items: attention?.awaitingInformation ?? [],
      },
      {
        title: 'Approved, not settled',
        icon: 'pi pi-clock',
        tone: 'danger',
        items: attention?.approvedNotSettled ?? [],
      },
      {
        title: 'Largest open exposure',
        icon: 'pi pi-chart-line',
        tone: 'teal',
        items: attention?.topExposure ?? [],
      },
    ];
  });

  readonly ratioChart = computed<ChartData<'bar'>>(() => {
    const ratios = this.dashboard()?.portfolio.settlementRatioPctByCurrency ?? {};
    const entries = Object.entries(ratios);
    return {
      labels: entries.map(([currency]) => currency),
      datasets: [
        {
          label: 'Settled vs claimed %',
          data: entries.map(([, ratio]) => ratio),
          backgroundColor: '#0f8a8a',
          borderRadius: 6,
        },
      ],
    };
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.dashboard(this.market() || null).subscribe({
      next: (dashboard) => {
        this.dashboard.set(dashboard);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.error.set(errorMessage(error, 'Could not load the portfolio dashboard.'));
      },
    });
  }

  onMarketChange(value: string): void {
    this.market.set(value ?? '');
    this.load();
  }

  ratioSummary(): string {
    const ratios = this.dashboard()?.portfolio.settlementRatioPctByCurrency ?? {};
    const entries = Object.entries(ratios);
    if (entries.length === 0) {
      return '—';
    }
    return entries.map(([currency, ratio]) => `${currency} ${ratio.toFixed(1)}%`).join(' · ');
  }

  attentionAmount(claim: ClaimAttention): string {
    const value = claim.reserveAmount ?? claim.claimedAmount;
    return money(value, claim.currency);
  }

  officerStatusCount(officerIndex: number, status: ClaimStatus): number {
    return this.dashboard()?.officerWorkload[officerIndex]?.byStatus[status] ?? 0;
  }
}

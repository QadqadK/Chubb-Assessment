import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import {
  FormBuilder,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { TimelineModule } from 'primeng/timeline';
import { forkJoin } from 'rxjs';
import { AuthService } from '../core/auth.service';
import {
  ClaimStatus,
  DecisionOutcome,
  OfficerClaimView,
  QueueItem,
  WorkloadResponse,
} from '../core/models';
import { OfficerApi } from '../core/officer-api';
import {
  ageLabel,
  claimTypeLabel,
  dateTime,
  errorMessage,
  eventIcon,
  money,
  shortDate,
  statusColor,
  statusLabel,
  statusSeverity,
} from '../core/ui';

@Component({
  selector: 'app-officer',
  imports: [
    ReactiveFormsModule,
    FormsModule,
    TableModule,
    TagModule,
    ButtonModule,
    DialogModule,
    InputTextModule,
    TextareaModule,
    InputNumberModule,
    SelectModule,
    TimelineModule,
  ],
  templateUrl: './officer.page.html',
  styleUrl: './officer.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OfficerPage implements OnInit {
  private readonly api = inject(OfficerApi);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(MessageService);

  readonly queue = signal<QueueItem[]>([]);
  readonly workload = signal<WorkloadResponse | null>(null);
  readonly queueLoading = signal(true);
  readonly workloadLoading = signal(true);
  readonly statusFilter = signal<ClaimStatus | ''>('');
  readonly detail = signal<OfficerClaimView | null>(null);
  readonly detailVisible = signal(false);
  readonly detailLoading = signal(false);
  readonly actionLoading = signal(false);
  readonly infoVisible = signal(false);
  readonly assessVisible = signal(false);
  readonly decisionVisible = signal(false);

  readonly statusSeverity = statusSeverity;
  readonly statusLabel = statusLabel;
  readonly claimTypeLabel = claimTypeLabel;
  readonly money = money;
  readonly shortDate = shortDate;
  readonly dateTime = dateTime;
  readonly ageLabel = ageLabel;
  readonly eventIcon = eventIcon;

  readonly statusOptions: Array<{ label: string; value: ClaimStatus | '' }> = [
    { label: 'All incoming', value: '' },
    { label: 'Submitted', value: 'SUBMITTED' },
    { label: 'Under review', value: 'UNDER_REVIEW' },
    { label: 'Info requested', value: 'INFO_REQUESTED' },
    { label: 'Assessed', value: 'ASSESSED' },
    { label: 'Approved', value: 'APPROVED' },
  ];

  readonly outcomeOptions = [
    { label: 'Approve', value: 'APPROVED' },
    { label: 'Reject', value: 'REJECTED' },
  ];

  readonly deskStatuses: Array<{ label: string; key: ClaimStatus; color: string }> = [
    { label: 'Review', key: 'UNDER_REVIEW', color: '#1f5fa8' },
    { label: 'Info', key: 'INFO_REQUESTED', color: '#d97706' },
    { label: 'Assessed', key: 'ASSESSED', color: '#7c3aed' },
    { label: 'Approved', key: 'APPROVED', color: '#0e9f6e' },
  ];

  readonly infoForm = this.fb.nonNullable.group({
    question: ['', [Validators.required, Validators.maxLength(2000)]],
  });
  readonly assessForm = this.fb.group({
    notes: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.maxLength(4000),
    ]),
    assessedAmount: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(0),
    ]),
  });
  readonly decisionForm = this.fb.group({
    outcome: this.fb.nonNullable.control<DecisionOutcome>('APPROVED', Validators.required),
    reason: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.maxLength(2000),
    ]),
    settlementAmount: this.fb.control<number | null>(null, [Validators.min(0)]),
  });

  readonly byStatus = computed(() => this.workload()?.byStatus ?? null);
  readonly assignedToMe = computed(
    () => this.detail()?.assignedOfficerId === this.auth.user()?.id,
  );
  readonly canRequestInfo = computed(
    () =>
      this.assignedToMe() &&
      (this.detail()?.status === 'UNDER_REVIEW' ||
        this.detail()?.status === 'INFO_REQUESTED'),
  );
  readonly canAssess = computed(
    () => this.assignedToMe() && this.detail()?.status === 'UNDER_REVIEW',
  );
  readonly canDecide = computed(
    () => this.assignedToMe() && this.detail()?.status === 'ASSESSED',
  );
  readonly canSettle = computed(
    () => this.assignedToMe() && this.detail()?.status === 'APPROVED',
  );
  readonly timeline = computed(() =>
    (this.detail()?.timeline ?? [])
      .slice()
      .reverse()
      .map((event) => ({
        ...event,
        icon: eventIcon(event.eventType),
        color: statusColor(event.toStatus ?? event.fromStatus ?? 'SUBMITTED'),
      })),
  );
  readonly detailEntries = computed(() => {
    const details = this.detail()?.incidentDetails ?? {};
    return Object.entries(details).map(([key, value]) => ({
      key: key.replace(/([A-Z])/g, ' $1').replace(/^./, (char) => char.toUpperCase()),
      value: String(value),
    }));
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.queueLoading.set(true);
    this.workloadLoading.set(true);
    forkJoin({
      queue: this.api.queue(this.statusFilter() || null),
      workload: this.api.workload(),
    }).subscribe({
      next: ({ queue, workload }) => {
        this.queue.set(queue.items);
        this.workload.set(workload);
        this.queueLoading.set(false);
        this.workloadLoading.set(false);
      },
      error: (error) => {
        this.queueLoading.set(false);
        this.workloadLoading.set(false);
        this.toast.add({
          severity: 'error',
          summary: 'Could not load the workspace',
          detail: errorMessage(error),
        });
      },
    });
  }

  loadQueue(): void {
    this.queueLoading.set(true);
    this.api.queue(this.statusFilter() || null).subscribe({
      next: (page) => {
        this.queue.set(page.items);
        this.queueLoading.set(false);
      },
      error: (error) => {
        this.queueLoading.set(false);
        this.toast.add({
          severity: 'error',
          summary: 'Could not refresh the queue',
          detail: errorMessage(error),
        });
      },
    });
  }

  onFilterChange(value: ClaimStatus | ''): void {
    this.statusFilter.set(value ?? '');
    this.loadQueue();
  }

  openClaim(id: string): void {
    this.detailVisible.set(true);
    this.detailLoading.set(true);
    this.detail.set(null);
    this.api.claim(id).subscribe({
      next: (claim) => {
        this.detail.set(claim);
        this.detailLoading.set(false);
      },
      error: (error) => {
        this.detailLoading.set(false);
        this.detailVisible.set(false);
        this.toast.add({
          severity: 'error',
          summary: 'Could not load claim',
          detail: errorMessage(error),
        });
      },
    });
  }

  refreshDetail(): void {
    const claim = this.detail();
    if (!claim) {
      return;
    }
    this.api.claim(claim.id).subscribe({
      next: (updated) => this.detail.set(updated),
    });
  }

  assign(id: string): void {
    this.actionLoading.set(true);
    this.api.assign(id).subscribe({
      next: (claim) => {
        this.actionLoading.set(false);
        this.toast.add({
          severity: 'success',
          summary: 'Claim picked up',
          detail: `${claim.reference} is now in your workload.`,
        });
        this.load();
        this.detail.set(claim);
        this.detailVisible.set(true);
      },
      error: (error) => {
        this.actionLoading.set(false);
        this.toast.add({
          severity: 'error',
          summary: 'Could not pick up claim',
          detail: errorMessage(error),
        });
      },
    });
  }

  openInfo(): void {
    this.infoForm.reset({ question: '' });
    this.infoVisible.set(true);
  }

  requestInfo(): void {
    const claim = this.detail();
    if (!claim || this.infoForm.invalid) {
      this.infoForm.markAllAsTouched();
      return;
    }
    this.actionLoading.set(true);
    this.api.requestInformation(claim.id, this.infoForm.getRawValue().question).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.infoVisible.set(false);
        this.toast.add({
          severity: 'success',
          summary: 'Information requested',
          detail: 'The claimant has been notified.',
        });
        this.afterAction();
      },
      error: (error) => this.failAction(error, 'Could not request information'),
    });
  }

  openAssess(): void {
    const claim = this.detail();
    this.assessForm.reset({
      notes: '',
      assessedAmount: claim?.latestAssessment?.assessedAmount ?? claim?.reserveAmount ?? null,
    });
    this.assessVisible.set(true);
  }

  assess(): void {
    const claim = this.detail();
    if (!claim || this.assessForm.invalid) {
      this.assessForm.markAllAsTouched();
      return;
    }
    const value = this.assessForm.getRawValue();
    if (value.assessedAmount === null) {
      return;
    }
    this.actionLoading.set(true);
    this.api.assess(claim.id, value.notes, value.assessedAmount).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.assessVisible.set(false);
        this.toast.add({
          severity: 'success',
          summary: 'Assessment recorded',
          detail: 'The claim is ready for a decision.',
        });
        this.afterAction();
      },
      error: (error) => this.failAction(error, 'Could not record the assessment'),
    });
  }

  openDecision(): void {
    const claim = this.detail();
    this.decisionForm.reset({
      outcome: 'APPROVED',
      reason: '',
      settlementAmount: claim?.latestAssessment?.assessedAmount ?? claim?.reserveAmount ?? null,
    });
    this.decisionVisible.set(true);
  }

  decide(): void {
    const claim = this.detail();
    if (!claim || this.decisionForm.invalid) {
      this.decisionForm.markAllAsTouched();
      return;
    }
    const value = this.decisionForm.getRawValue();
    this.actionLoading.set(true);
    this.api
      .decide(claim.id, value.outcome, value.reason, value.settlementAmount)
      .subscribe({
        next: () => {
          this.actionLoading.set(false);
          this.decisionVisible.set(false);
          this.toast.add({
            severity: 'success',
            summary: 'Decision recorded',
            detail: `Claim ${claim.reference} is now ${value.outcome.toLowerCase()}.`,
          });
          this.afterAction();
        },
        error: (error) => this.failAction(error, 'Could not record the decision'),
      });
  }

  settle(): void {
    const claim = this.detail();
    if (!claim) {
      return;
    }
    this.actionLoading.set(true);
    this.api.settle(claim.id).subscribe({
      next: () => {
        this.actionLoading.set(false);
        this.toast.add({
          severity: 'success',
          summary: 'Claim settled',
          detail: `${claim.reference} is closed.`,
        });
        this.afterAction();
      },
      error: (error) => this.failAction(error, 'Could not settle the claim'),
    });
  }

  private afterAction(): void {
    this.refreshDetail();
    this.load();
  }

  private failAction(error: unknown, summary: string): void {
    this.actionLoading.set(false);
    this.toast.add({
      severity: 'error',
      summary,
      detail: errorMessage(error),
    });
  }

  transitionLabel(event: { fromStatus: ClaimStatus | null; toStatus: ClaimStatus | null; eventType: string }): string {
    if (event.fromStatus && event.toStatus) {
      return `${statusLabel(event.fromStatus)} → ${statusLabel(event.toStatus)}`;
    }
    return statusLabel(event.toStatus) || event.eventType;
  }
}

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CLAIMS_API } from './api-config';
import {
  AssessmentView,
  ClaimStatus,
  ClaimSummary,
  DecisionOutcome,
  DecisionView,
  InfoRequestView,
  OfficerClaimView,
  PageResponse,
  QueueItem,
  WorkloadResponse,
} from './models';

@Injectable({ providedIn: 'root' })
export class OfficerApi {
  private readonly http = inject(HttpClient);

  queue(status?: ClaimStatus | null, page = 0, size = 50) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<PageResponse<QueueItem>>(`${CLAIMS_API}/officer/queue`, { params });
  }

  workload() {
    return this.http.get<WorkloadResponse>(`${CLAIMS_API}/officer/me/workload`);
  }

  claim(id: string) {
    return this.http.get<OfficerClaimView>(`${CLAIMS_API}/officer/claims/${id}`);
  }

  assign(id: string) {
    return this.http.post<OfficerClaimView>(`${CLAIMS_API}/claims/${id}/assign`, {});
  }

  requestInformation(id: string, question: string) {
    return this.http.post<InfoRequestView>(`${CLAIMS_API}/claims/${id}/info-requests`, { question });
  }

  assess(id: string, notes: string, assessedAmount: number) {
    return this.http.post<AssessmentView>(`${CLAIMS_API}/claims/${id}/assessment`, {
      notes,
      assessedAmount,
    });
  }

  decide(
    id: string,
    outcome: DecisionOutcome,
    reason: string,
    settlementAmount: number | null,
  ) {
    const body: { outcome: DecisionOutcome; reason: string; settlementAmount?: number } = {
      outcome,
      reason,
    };
    if (settlementAmount !== null && settlementAmount !== undefined) {
      body.settlementAmount = settlementAmount;
    }
    return this.http.post<DecisionView>(`${CLAIMS_API}/claims/${id}/decision`, body);
  }

  settle(id: string) {
    return this.http.post<ClaimSummary>(`${CLAIMS_API}/claims/${id}/settle`, {});
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CLAIMS_API } from './api-config';
import {
  ClaimDetail,
  ClaimSummary,
  CreateClaimRequest,
  DocumentView,
  InfoRequestView,
  PolicyView,
} from './models';

@Injectable({ providedIn: 'root' })
export class ClaimantApi {
  private readonly http = inject(HttpClient);

  policies() {
    return this.http.get<PolicyView[]>(`${CLAIMS_API}/policies`);
  }

  claims() {
    return this.http.get<ClaimSummary[]>(`${CLAIMS_API}/claims`);
  }

  claim(id: string) {
    return this.http.get<ClaimDetail>(`${CLAIMS_API}/claims/${id}`);
  }

  submit(body: CreateClaimRequest) {
    return this.http.post<ClaimSummary>(`${CLAIMS_API}/claims`, body);
  }

  respond(claimId: string, requestId: string, response: string) {
    return this.http.post<InfoRequestView>(
      `${CLAIMS_API}/claims/${claimId}/info-requests/${requestId}/response`,
      { response },
    );
  }

  upload(claimId: string, file: File) {
    const body = new FormData();
    body.append('file', file);
    return this.http.post<DocumentView>(`${CLAIMS_API}/claims/${claimId}/documents`, body);
  }
}

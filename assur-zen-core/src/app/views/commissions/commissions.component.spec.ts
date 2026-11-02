import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { IconSetService } from '@coreui/icons-angular';
import { of, throwError } from 'rxjs';

import { CommissionsComponent } from './commissions.component';
import { CommissionService } from '../../core/commissions/commission.service';
import { ContractCommission } from '../../core/commissions/models/commission.model';
import { SycaPayService } from '../../core/sycapay/sycapay.service';
import { SycaPayTransaction } from '../../core/sycapay/models/sycapay.model';

/** Paiement manuel refusé en 409 SYCAPAY_DUPLICATE_PAYMENT (2026-09-25) : la transaction de
 * chaque contrat sélectionné est retrouvée via ?contractId pour proposer « Réconcilier ». */
describe('CommissionsComponent — paiement bloqué par un virement SycaPay', () => {
  let component: CommissionsComponent;
  let transactionsByContract: Record<number, Partial<SycaPayTransaction> | null>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CommissionsComponent],
      providers: [IconSetService, provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    transactionsByContract = {};
    vi.spyOn(TestBed.inject(SycaPayService), 'getContractTransaction').mockImplementation((id) =>
      of((transactionsByContract[id] ?? null) as SycaPayTransaction | null),
    );
    vi.spyOn(TestBed.inject(CommissionService), 'pay').mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { code: 'SYCAPAY_DUPLICATE_PAYMENT', message: 'Un paiement SycaPay est deja en cours...' },
          }),
      ),
    );

    component = TestBed.createComponent(CommissionsComponent).componentInstance;
    component.contracts.set([
      { contractId: 12, policyNumber: 'CTR-12', commissionStatus: 'pending' },
      { contractId: 13, policyNumber: 'CTR-13', commissionStatus: 'pending' },
      { contractId: 14, policyNumber: 'CTR-14', commissionStatus: 'pending' },
      { contractId: 6, policyNumber: 'CTR-6', commissionStatus: 'pending' },
    ] as ContractCommission[]);
  });

  function payContracts(ids: number[]): void {
    component.selectedContractIds.set(new Set(ids));
    component.payCommission();
  }

  it('UNKNOWN / PROCESSING : réconciliables ; PENDING : à attendre ; FAILED ou sans virement : ignorés', () => {
    transactionsByContract = {
      12: { id: 112, contractId: 12, status: 'UNKNOWN' },
      13: { id: 113, contractId: 13, status: 'PROCESSING' },
      14: { id: 114, contractId: 14, status: 'PENDING' },
      6: { id: 106, contractId: 6, status: 'FAILED' },
    };

    payContracts([12, 13, 14, 6]);

    expect(component.payError()).toContain('déjà en cours');
    expect(component.payBlockedTransactions()).toEqual([
      { transactionId: 112, policyNumber: 'CTR-12', status: 'UNKNOWN', reconcilable: true },
      { transactionId: 113, policyNumber: 'CTR-13', status: 'PROCESSING', reconcilable: true },
      { transactionId: 114, policyNumber: 'CTR-14', status: 'PENDING', reconcilable: false },
    ]);
  });

  it('un autre refus que SYCAPAY_DUPLICATE_PAYMENT ne cherche aucune transaction', () => {
    const lookup = vi.spyOn(TestBed.inject(SycaPayService), 'getContractTransaction');
    vi.spyOn(TestBed.inject(CommissionService), 'pay').mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );

    payContracts([12]);

    expect(lookup).not.toHaveBeenCalled();
    expect(component.payBlockedTransactions()).toEqual([]);
  });
});

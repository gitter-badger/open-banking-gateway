export * from './finTechAccountInformation.service';
import { FinTechAccountInformationService } from './finTechAccountInformation.service';
export * from './finTechAuthorization.service';
import { FinTechAuthorizationService } from './finTechAuthorization.service';
export * from './finTechBankSearch.service';
import { FinTechBankSearchService } from './finTechBankSearch.service';
export const APIS = [FinTechAccountInformationService, FinTechAuthorizationService, FinTechBankSearchService];

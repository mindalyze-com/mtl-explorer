import { LocationSearchControllerApi, type LocationSearchStatusDto } from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import { createStatusRequestService } from '@/utils/statusPolling';

const locationSearchStatusRequests = createStatusRequestService<LocationSearchStatusDto>({
  request: (signal) => new LocationSearchControllerApi(getApiConfiguration()).getStatus({ signal }),
});

export function fetchLocationSearchStatus(): Promise<LocationSearchStatusDto> {
  return locationSearchStatusRequests.fetch();
}

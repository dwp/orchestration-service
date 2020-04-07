package uk.gov.dwp.dataworks.exceptions

/** Used when specified request cannot be executed. */
class ErrorWhileProcessingRequestException (request: String) : Exception("Error while processing the $request request")
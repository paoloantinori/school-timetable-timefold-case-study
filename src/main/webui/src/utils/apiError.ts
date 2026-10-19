import { AxiosError } from 'axios';

/**
 * API error response structure from the backend.
 */
interface ApiErrorResponse {
  error?: string;
  message?: string;
}

/**
 * Type guard to check if an error is an AxiosError.
 */
export function isAxiosError(error: unknown): error is AxiosError {
  return error !== null &&
    typeof error === 'object' &&
    'isAxiosError' in error;
}

/**
 * Extracts a user-friendly error message from various error types.
 *
 * Priority:
 * 1. Axios response data error/message
 * 2. Error instance message
 * 3. String error
 * 4. Fallback message
 */
export function getErrorMessage(
  error: unknown,
  fallback = 'An unexpected error occurred'
): string {
  // Handle Axios errors
  if (isAxiosError(error)) {
    const data = error.response?.data as ApiErrorResponse | undefined;
    const apiMessage = data?.error || data?.message;

    if (apiMessage) {
      return typeof apiMessage === 'string'
        ? apiMessage
        : JSON.stringify(apiMessage);
    }

    if (error.message) {
      return error.message;
    }

    return fallback;
  }

  // Handle standard Error instances
  if (error instanceof Error) {
    return error.message || fallback;
  }

  // Handle string errors
  if (typeof error === 'string') {
    return error || fallback;
  }

  // Handle object errors with message property
  if (error && typeof error === 'object' && 'message' in error) {
    const msg = (error as { message: unknown }).message;
    return typeof msg === 'string' ? msg : fallback;
  }

  return fallback;
}

/**
 * Wraps an async function with standardized error handling.
 * Returns the result or throws a normalized error message.
 */
export async function withErrorHandling<T>(
  fn: () => Promise<T>,
  errorContext?: string
): Promise<T> {
  try {
    return await fn();
  } catch (error) {
    const message = getErrorMessage(error);
    throw new Error(errorContext ? `${errorContext}: ${message}` : message);
  }
}

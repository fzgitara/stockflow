/** Shape of every non-2xx response from the backend (ErrorResponse.java). */
export interface ApiError {
  status: number;
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
  timestamp: string;
}

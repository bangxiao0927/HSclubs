// The native app tags the school WebView's user agent (see hsclubs-app, MobileAuthConfig). The web
// app uses that marker for one thing only: to send the login button to the fixed mobile-auth entry
// the app intercepts, instead of the browser's OAuth redirect. The marker is never treated as a
// credential -- it only changes which URL the button points at, and the backend still validates
// every mobile-auth request on its own.
const APP_USER_AGENT_MARKER = 'mobile-auth/'
const APP_NAME_MARKER = 'HSclubsApp/'

/** The fixed sign-in entry the app intercepts on this origin. */
export const MOBILE_AUTH_START_PATH = '/api/mobile-auth/start'

export const isHSclubsAppUserAgent = (
  userAgent: string | undefined = typeof navigator === 'undefined' ? undefined : navigator.userAgent,
): boolean => {
  if (!userAgent) return false
  return userAgent.includes(APP_NAME_MARKER) && userAgent.includes(APP_USER_AGENT_MARKER)
}

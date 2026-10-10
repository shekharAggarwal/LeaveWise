import {useSyncExternalStore} from 'react';

/** The canvas's phone layout applies below 800 px. Tests (jsdom) get the desktop layout unless they stub matchMedia. */
export const PHONE_QUERY = '(max-width: 799px)';

function subscribe(onChange: () => void) {
  const list = window.matchMedia(PHONE_QUERY);
  list.addEventListener('change', onChange);
  return () => list.removeEventListener('change', onChange);
}

export function useIsPhone(): boolean {
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(PHONE_QUERY).matches,
    () => false,
  );
}

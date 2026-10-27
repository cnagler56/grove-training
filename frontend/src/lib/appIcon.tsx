import { ImageResponse } from 'next/og';

/** The header's steel blue: the app icon background and the phone's status bar color. */
export const BRAND_BLUE = '#4682b4';

/**
 * The app icon: a white "G" on steel blue, filling the whole square so phones
 * can round or mask the corners themselves. The letter stays inside the
 * middle 80% so Android's circle mask never clips it.
 */
export function appIcon(size: number) {
  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: BRAND_BLUE,
          color: 'white',
          fontSize: Math.round(size * 0.58),
          fontWeight: 800,
          letterSpacing: '-0.02em',
        }}
      >
        G
      </div>
    ),
    { width: size, height: size },
  );
}

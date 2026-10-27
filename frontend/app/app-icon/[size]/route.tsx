import { appIcon } from '@/src/lib/appIcon';

/** Android home-screen and install icons, listed in the manifest. Made once at build time. */
const SIZES = ['192', '512'];

export function generateStaticParams() {
  return SIZES.map((size) => ({ size }));
}

export const dynamicParams = false;

export async function GET(_req: Request, { params }: { params: Promise<{ size: string }> }) {
  const { size } = await params;
  return appIcon(Number(size));
}

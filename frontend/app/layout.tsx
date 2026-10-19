import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Grove Salons Training",
  description: "Training videos for the Grove Salons team.",
  // Staff-only site: keep it out of search engines.
  robots: { index: false, follow: false },
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}

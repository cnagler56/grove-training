/**
 * Salon details for the public home page. Fill these in; anything left empty
 * is simply hidden on the page.
 */
export const SALON = {
  name: 'Grove Salons',
  tagline: 'Great hair, friendly faces.',
  intro:
    'From quick trims to full color makeovers, our stylists take the time to get it right. Walk in or call ahead, whatever works for you.',

  /** e.g. "(555) 555-0100". Shown as a tap-to-call button. */
  phone: '',
  /** Street address, one line per entry. */
  address: [] as string[],
  /** e.g. [["Mon–Fri", "9am–8pm"], ["Sat", "9am–6pm"], ["Sun", "10am–5pm"]] */
  hours: [] as [string, string][],
  /** Online booking link, if you have one. */
  bookingUrl: '',
  /** Full Instagram/Facebook links. */
  instagram: '',
  facebook: '',

  services: [
    { name: 'Haircuts', blurb: 'Classic cuts and fresh styles for women, men, and kids.', icon: '✂️' },
    { name: 'Color', blurb: 'All-over color, gray coverage, and glosses.', icon: '🎨' },
    { name: 'Highlights & balayage', blurb: 'Sun-kissed dimension, foils or hand-painted.', icon: '✨' },
    { name: 'Blowouts & styling', blurb: 'Smooth, bouncy, or ready for a big night.', icon: '💨' },
    { name: 'Kids’ cuts', blurb: 'Patient stylists and quick, happy cuts.', icon: '🧒' },
    { name: 'Treatments', blurb: 'Deep conditioning and scalp care.', icon: '💧' },
  ],
};

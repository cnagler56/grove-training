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
  phone: '(763) 391-6664',
  /** Street address, one line per entry. */
  address: ['9859 Maple Grove Pkwy', 'Maple Grove, MN 55369'] as string[],
  /** e.g. [["Mon–Fri", "9am–8pm"], ["Sat", "9am–6pm"], ["Sun", "10am–5pm"]] */
  hours: [
    ['Monday – Thursday', '10am – 8pm'],
    ['Friday', '10am – 6pm'],
    ['Saturday', '9am – 5pm'],
    ['Sunday', '11am – 4pm'],
  ] as [string, string][],
  /** Same hours by weekday, Sunday first, for "Open today" on the staff home page. '' = closed. */
  hoursByDay: ['11am – 4pm', '10am – 8pm', '10am – 8pm', '10am – 8pm', '10am – 8pm', '10am – 6pm', '9am – 5pm'],
  /** Same hours as 24-hour [open, close], Sunday first. The schedule editor starts new shifts from these. */
  openClose: [
    ['11:00', '16:00'], ['10:00', '20:00'], ['10:00', '20:00'], ['10:00', '20:00'],
    ['10:00', '20:00'], ['10:00', '18:00'], ['09:00', '17:00'],
  ] as [string, string][],
  /** Online booking link, if you have one. */
  bookingUrl: 'https://fsnagel.bookedby.com/new-order/select-service?storeUid=fcffab0b-af9c-4c79-b49c-7b84f71777f7',
  /** Full Instagram/Facebook links. */
  instagram: '',
  facebook: '',

  /** Shown under the price menu. Update with the price list. */
  pricesAsOf: 'June 2025',

  /**
   * The price menu on the public home page, from "MG June 2025-2.pdf"
   * (Dropbox > Salon Documents > Pricing > Price Lists). Each price has an
   * optional label ("starting at", "stand alone", ...) shown before it.
   */
  priceList: [
    {
      section: 'Haircuts',
      note: 'Cut, shampoo, hot towel, finger tousle blow-dry',
      items: [
        { name: 'Adult', prices: [{ amount: 29 }] },
        { name: 'Senior', detail: '65+', prices: [{ amount: 27 }] },
        { name: 'Buzz Cut', detail: 'Same size guard all over', prices: [{ amount: 25 }] },
        { name: 'Kid', detail: '11 & younger', prices: [{ amount: 27 }] },
        { name: 'Bang or Beard Trim', prices: [{ amount: 15 }] },
        { name: 'Neck Trim', prices: [{ amount: 8 }] },
      ],
    },
    {
      section: 'Color',
      note: 'Starting at chin length; longer hair and additional color extra',
      items: [
        { name: 'Root Retouch 1" Gloss or Permanent Color', detail: 'One step all over color', prices: [{ label: 'starting at', amount: 70 }] },
        { name: 'Gloss or Permanent Color', detail: 'One step all over color', prices: [{ label: 'starting at', amount: 75 }] },
        { name: 'Area Foil', detail: '10 foils; highlights or lowlights; one color', prices: [{ label: 'starting at', amount: 65 }] },
        { name: 'Partial Foil', detail: '30 foils or less; highlights or lowlights; one color', prices: [{ label: 'starting at', amount: 80 }] },
        { name: 'Full Foil', detail: 'Full head of highlights or lowlights; one color', prices: [{ label: 'starting at', amount: 95 }] },
        { name: 'Fashion Vivid Tips', detail: '3 inches', prices: [{ label: 'starting at', amount: 65 }] },
        { name: 'Fashion Vivid Color', detail: 'One color foils or one step all over', prices: [{ label: 'starting at', amount: 100 }] },
        { name: 'Balayage, Ombre, Specialty Color, Corrective Color', prices: [{ label: 'starting at', amount: 160 }] },
        { name: 'Eyebrow Tint', prices: [{ amount: 22 }] },
        { name: 'Men’s Color', prices: [{ label: 'grey blending', amount: 40 }, { label: 'full coverage', amount: 70 }] },
      ],
    },
    {
      section: 'Treatments',
      note: 'Shampoo, hot towel, and finger tousle blow-dry',
      items: [
        { name: 'Ultimate Healing Treatment', detail: 'Adds moisture, protein or volume', prices: [{ label: 'stand alone', amount: 25 }, { label: 'with any service', amount: 20 }] },
        { name: 'Keratin Healing Oil Emergency Treatment', detail: 'Provides healing to severely damaged hair', prices: [{ label: 'stand alone', amount: 30 }, { label: 'with any service', amount: 25 }] },
        { name: 'Malibu Makeover', detail: 'Removes hard water, chlorine or mineral build-up', prices: [{ label: 'stand alone', amount: 30 }, { label: 'with any service', amount: 25 }] },
      ],
    },
    {
      section: 'Waxing',
      items: [
        { name: 'Facial Wax', detail: 'Eyebrow, lip or chin', prices: [{ label: 'each area', amount: 17 }] },
        { name: 'Unibrow, Nose or Ear Hair Removal', prices: [{ label: 'each area', amount: 10 }] },
      ],
    },
    {
      section: 'Styling',
      note: 'Starting at chin length; longer hair and hot tools extra',
      items: [
        { name: 'Senior Shampoo Style', detail: '60+, with brush or curling iron, no haircut', prices: [{ label: 'chin length', amount: 27 }] },
        { name: 'Style with Haircut or Color', prices: [{ label: 'starting at', amount: 10 }] },
        { name: 'Blow Out without Haircut', detail: 'With or without shampoo', prices: [{ label: 'starting at', amount: 43 }] },
        { name: 'Textured Hair Style', detail: 'With or without shampoo', prices: [{ label: 'starting at', amount: 70 }] },
        { name: 'Hot Tools', detail: 'Curling or flat iron, with a blow out or textured style', prices: [{ label: 'add on', amount: 10 }] },
        { name: 'Updo or Special Occasion Styling', detail: 'Formal styling, updos, braids, etc., including irons; no shampoo', prices: [{ label: 'starting at', amount: 75 }] },
      ],
    },
    {
      section: 'Texture',
      note: 'Up to shoulder length',
      items: [
        { name: 'Perm', detail: 'Basic wrapped perm; includes finger tousle blow-dry', prices: [{ label: 'starting at', amount: 90 }] },
      ],
    },
  ] as PriceSection[],
};

export type PriceSection = {
  section: string;
  note?: string;
  items: { name: string; detail?: string; prices: { label?: string; amount: number }[] }[];
};

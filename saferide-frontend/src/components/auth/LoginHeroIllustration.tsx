/**
 * A flat-vector interpretation of "a car on a route, under a pin" - the
 * app has no image-generation capability and no illustration asset
 * pipeline, so this is built the same way every marker/icon elsewhere in
 * SafeRide is (plain inline SVG shapes in the app's own palette), not a
 * literal copy of a photographic reference mockup.
 */
export function LoginHeroIllustration() {
  return (
    <svg viewBox="0 0 400 220" fill="none" className="w-full" aria-hidden="true">
      {/* Faint skyline */}
      <g opacity="0.35">
        <rect x="18" y="120" width="26" height="70" rx="2" fill="#CBD5E1" />
        <rect x="52" y="95" width="22" height="95" rx="2" fill="#CBD5E1" />
        <rect x="330" y="110" width="24" height="80" rx="2" fill="#CBD5E1" />
        <rect x="360" y="85" width="20" height="105" rx="2" fill="#CBD5E1" />
      </g>

      {/* Clouds */}
      <g fill="#E2E8F0">
        <ellipse cx="70" cy="30" rx="22" ry="11" />
        <ellipse cx="88" cy="24" rx="16" ry="9" />
        <ellipse cx="330" cy="24" rx="18" ry="9" />
      </g>

      {/* Dotted route from the car up to a small destination pin */}
      <path
        d="M235 150 C 270 140, 300 120, 300 90 S 330 55, 355 45"
        stroke="#F5B800"
        strokeWidth="2.5"
        strokeDasharray="1 8"
        strokeLinecap="round"
        fill="none"
      />
      {/* Small destination pin */}
      <g transform="translate(355 45)">
        <path d="M0 -14C-6 -14-11-9-11-3 -11 6 0 16 0 16S11 6 11-3C11-9 6-14 0-14Z" fill="#0F172A" />
        <circle r="4" fill="white" />
      </g>

      {/* Large origin pin, ring style, above the car */}
      <g transform="translate(180 60)">
        <path d="M0 -46C-24 -46-44-28-44-8 -44 24 0 66 0 66S44 24 44-8C44-28 24-46 0-46Z" fill="#F5B800" />
        <circle r="17" fill="#FFF7E0" />
      </g>

      {/* Road */}
      <path d="M20 190 Q 200 165 380 190" stroke="#E2E8F0" strokeWidth="26" strokeLinecap="round" />
      <path
        d="M20 190 Q 200 165 380 190"
        stroke="#F5B800"
        strokeWidth="3"
        strokeDasharray="14 10"
        strokeLinecap="round"
        opacity="0.6"
      />

      {/* Car (flat side view) */}
      <g transform="translate(120 132)">
        <path
          d="M4 44 L4 30 C4 24 8 20 14 19 L34 16 L46 4 C49 1 53 -1 57 -1 L108 -1 C114 -1 119 2 122 7 L132 19 L146 20 C152 20.5 156 25 156 31 L156 44 Z"
          fill="white"
          stroke="#0F172A"
          strokeWidth="2.5"
        />
        <path d="M50 16 L60 3 L105 3 L116 16 Z" fill="#DCEEFF" stroke="#0F172A" strokeWidth="2" />
        <circle cx="34" cy="44" r="13" fill="#0F172A" />
        <circle cx="34" cy="44" r="5" fill="#CBD5E1" />
        <circle cx="128" cy="44" r="13" fill="#0F172A" />
        <circle cx="128" cy="44" r="5" fill="#CBD5E1" />
        <rect x="70" y="18" width="14" height="6" rx="2" fill="#F5B800" />
      </g>
    </svg>
  );
}

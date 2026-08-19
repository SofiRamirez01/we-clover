import type { SVGProps } from 'react';

export default function CloverIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      width={30}
      height={30}
      viewBox="0 0 24 24"
      fill="currentColor"
      stroke="none"
      {...props}
    >
      <path d="M12 2c-2 0-3.5 1.6-3.5 3.5c0 1 .4 1.9 1.1 2.6C8 7.5 6.5 8 5.7 9.3C4.6 11 5 13.2 6.7 14.3c1 .6 2.1.8 3.1.5c-1.1 1.9-.7 3.9.6 5.1c1.5 1.4 3.8 1.4 5.2 0c1.3-1.2 1.7-3.2.6-5.1c1 .3 2.1.1 3.1-.5c1.7-1.1 2.1-3.3 1-5c-.8-1.3-2.3-1.8-3.9-1.2c.7-.7 1.1-1.6 1.1-2.6C17.5 3.6 16 2 14 2c-1 0-1.8.4-2 1c-.2-.6-1-1-2-1Z" />
    </svg>
  );
}

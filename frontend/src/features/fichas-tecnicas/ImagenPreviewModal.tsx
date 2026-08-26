import { useEffect } from 'react';

interface ImagenPreviewModalProps {
  src: string;
  alt: string;
  onClose: () => void;
}

export default function ImagenPreviewModal({ src, alt, onClose }: ImagenPreviewModalProps) {
  useEffect(() => {
    function alPresionarTecla(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', alPresionarTecla);
    return () => document.removeEventListener('keydown', alPresionarTecla);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-6"
      onClick={onClose}
      role="dialog"
      aria-modal="true"
    >
      <div className="relative max-h-[85vh] max-w-3xl" onClick={(e) => e.stopPropagation()}>
        <img src={src} alt={alt} className="max-h-[85vh] max-w-full rounded-lg object-contain shadow-2xl" />
        <button
          type="button"
          onClick={onClose}
          aria-label="Cerrar"
          className="absolute -right-3 -top-3 flex h-8 w-8 items-center justify-center rounded-full border-0 bg-white text-wc-text shadow-md outline-none transition hover:bg-wc-bg"
        >
          ✕
        </button>
      </div>
    </div>
  );
}

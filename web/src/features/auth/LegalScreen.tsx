import { useNavigate, useParams } from 'react-router';
import { Button } from '@/ui/Button';
import { Dialog } from '@/ui/Dialog';
import { AuthLayout } from './AuthLayout';
import { LEGAL_BODY, LEGAL_DOCUMENTS, LEGAL_TITLE, LEGAL_UPDATED, type LegalDocument } from './legal';

function LegalText({ document }: { document: LegalDocument }) {
  return (
    <div className="flex flex-col gap-5">
      {LEGAL_BODY[document].map(([heading, body], i) => (
        <section key={heading}>
          <h2 className="font-semibold text-ivory">
            {i + 1}. {heading}
          </h2>
          <p className="mt-1.5 text-[15px] leading-relaxed text-ivory-dim">{body}</p>
        </section>
      ))}
      <p className="text-sm text-mute">{LEGAL_UPDATED}</p>
    </div>
  );
}

export function LegalScreen() {
  const { document } = useParams();
  const navigate = useNavigate();
  const doc: LegalDocument = LEGAL_DOCUMENTS.find((d) => d === document) ?? 'terminos';
  return (
    <AuthLayout title={LEGAL_TITLE[doc]}>
      <LegalText document={doc} />
      <Button variant="secondary" block className="mt-8" onClick={() => navigate(-1)}>
        Volver
      </Button>
    </AuthLayout>
  );
}

/** El documento sin salir de la pantalla (p. ej. del registro, para no perder lo escrito). */
export function LegalDialog({ document, onClose }: { document: LegalDocument | null; onClose: () => void }) {
  return (
    <Dialog open={document !== null} onClose={onClose} title={document ? LEGAL_TITLE[document] : ''} sheet actions={<Button onClick={onClose}>Entendido</Button>}>
      {document && <LegalText document={document} />}
    </Dialog>
  );
}

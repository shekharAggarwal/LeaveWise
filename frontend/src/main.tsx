import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import '@astryxdesign/core/reset.css';
import './theme/leavewise.css';
// The LeaveWise theme (Stone's typography) names Figtree (body) and Montserrat (headings); self-hosted, so no third-party requests.
import '@fontsource/figtree/latin-400.css';
import '@fontsource/figtree/latin-500.css';
import '@fontsource/figtree/latin-600.css';
import '@fontsource/figtree/latin-700.css';
import '@fontsource/montserrat/latin-600.css';
import '@fontsource/montserrat/latin-700.css';
import '@fontsource/montserrat/latin-800.css';
import './index.css';
import {App} from './App';

const root = document.getElementById('root');
if (root === null) {
  throw new Error('index.html must contain an element with id "root".');
}

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

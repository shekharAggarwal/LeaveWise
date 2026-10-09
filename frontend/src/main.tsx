import {StrictMode} from 'react';
import {createRoot} from 'react-dom/client';
import '@astryxdesign/core/reset.css';
import '@astryxdesign/theme-stone/theme.css';
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

import layerPages from './layer';
import projectPage from './project';
import layersPage from './layers';
import settingsPages from './settings';
import analysesPages from './analyses';

export default [
    layersPage,
    projectPage,
    ...layerPages,
    ...settingsPages,
    ...analysesPages
];

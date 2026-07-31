import type { CapacitorConfig } from '@capacitor/cli';

// Personal-use-only iOS shell. Not intended for App Store distribution.
const config: CapacitorConfig = {
  appId: 'world.phantasmal.mobilegame',
  appName: 'Phantasmal World Mobile',
  webDir: '../web/mobileGame/build/dist/js/productionExecutable',
  ios: {
    contentInset: 'never',
  },
};

export default config;

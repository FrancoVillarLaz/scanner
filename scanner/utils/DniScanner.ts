
import { NativeModules, Platform } from 'react-native';

const { DniScanner } = NativeModules;

if (!DniScanner && __DEV__) {
  console.error("Módulo DniScanner no encontrado. Revisa el autolinking y el build nativo.");
}

/**
 * Inicia el escaneo de DNI y devuelve el resultado.
 * @returns Promesa con el JSON String del resultado del escaneo.
 */
export async function scanDni(): Promise<string> {
  if (Platform.OS !== 'android') {
    return Promise.reject(new Error('Módulo DniScanner solo en Android.'));
  }
  
  if (!DniScanner || !DniScanner.scan) {
      return Promise.reject(new Error('El método scan() no está disponible.'));
  }
  
  try {
    // Llama al @ReactMethod de Kotlin
    const jsonResult: string = await DniScanner.scan();
    return jsonResult;
  } catch (error) {
    throw error; 
  }
}
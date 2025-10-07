// app/(tabs)/scanner.tsx (Si quieres que sea una pestaña)

import React, { useState } from 'react';
import { ActivityIndicator, Alert, Button, StyleSheet, Text } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { scanDni } from '../../utils/DniScanner';

export default function ScannerScreen() {
  const [data, setData] = useState('Presiona para iniciar el escaneo del DNI.');
  const [loading, setLoading] = useState(false);

  const handleScan = async () => {
    if (loading) return;

    setLoading(true);
    setData('Iniciando escaneo...');

    try {
      // LLAMADA AL MÓDULO
      const resultJson = await scanDni(); 
      
      const resultObject = JSON.parse(resultJson);
      setData(`✅ Escaneo OK. Nombre: ${resultObject.nombre || 'N/A'}, DNI: ${resultObject.dni || 'N/A'}`);
      
    } catch (e: any) {
      const code = e.code || 'UNKNOWN';
      const message = e.message || 'Error desconocido.';

      if (code === 'CANCELED' || code === 'USER_CANCELED') {
        setData('Escaneo cancelado por el usuario o el sistema.');
      } else {
        setData('❌ FALLO: ' + message);
        Alert.alert(`Error de Escaneo (${code})`, message);
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.header}>Escáner Nativo DNI</Text>
      
      {loading && <ActivityIndicator size="large" color="#0000ff" />}

      <Button 
        title={loading ? "Esperando resultado..." : "Iniciar Escaneo"} 
        onPress={handleScan} 
        disabled={loading}
      />
      
      <Text style={styles.status}>{data}</Text>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 20 },
  header: { fontSize: 24, marginBottom: 30, fontWeight: 'bold' },
  status: { marginTop: 30, textAlign: 'center', paddingHorizontal: 20 },
});
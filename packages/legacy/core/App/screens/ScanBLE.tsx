/* eslint-disable @typescript-eslint/no-use-before-define */
/* eslint-disable no-console */
import React, { useState, useEffect, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  PermissionsAndroid,
  SafeAreaView,
  View,
  Text,
  FlatList,
  StyleSheet,
  Alert,
  Platform,
  Pressable,
  Switch,
  NativeModules,
  NativeEventEmitter,
} from 'react-native'
// import BleAdvertiser from 'react-native-ble-advertiser'
import BleManager from 'react-native-ble-manager'

import Button, { ButtonType } from '../components/buttons/Button'
import { useAnimatedComponents } from '../contexts/animated-components'
import { useTheme } from '../contexts/theme'
import { testIdWithKey } from '../utils/testable'

const { BleAdvertise } = NativeModules

// Define the local device interface for TypeScript
interface LocalDevice {
  id: string
  name?: string
  rssi?: number
  advertising?: Advertising
}

interface Advertising {
  serviceUUIDs: string[]
}

// Styling for the component
const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 10,
  },
  device: {
    marginVertical: 5,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  deviceName: {
    fontWeight: 'bold',
  },
  noDevicesText: {
    textAlign: 'center',
    marginTop: 20,
  },
})

const ScanBLE = () => {
  const [isScanning, setIsScanning] = useState(false)
  const [devices, setDevices] = useState<LocalDevice[]>([])
  const [discoverable, setDiscoverable] = useState<boolean>()
  const [connectedDeviceId, setConnectedDeviceId] = useState<string | null>(null)
  const BleManagerModule = NativeModules.BleManager
  const BleManagerEmitter = new NativeEventEmitter(BleManagerModule)
  const { ColorPallet, TextTheme } = useTheme()
  const { ButtonLoading } = useAnimatedComponents()
  const { t } = useTranslation()
  const uuid = '1357d860-1eb6-11ef-9e35-0800200c9a66'

  BleAdvertise.setCompanyId(0x00e0)

  const handleDiscoverPeripheral = (peripheral: LocalDevice) => {
    console.log(peripheral)
    if (peripheral && peripheral.id && peripheral.name) {
      setDevices((prevDevices) => {
        const deviceExists = prevDevices.some((device) => device.id === peripheral.id)
        if (!deviceExists) console.log(peripheral)
        return deviceExists
          ? prevDevices
          : [...prevDevices, { id: peripheral.id, name: peripheral.name, rssi: peripheral.rssi }]
      })
    }
  }

  useEffect(() => {
    BleManager.start({ showAlert: false }).catch((error) => {
      console.error('BleManager initialization error:', error)
    })

    const stopListener = BleManagerEmitter.addListener('BleManagerStopScan', () => {
      setIsScanning(false)
      console.log('Scan is stopped')
    })

    const discoverListener = BleManager.addListener('BleManagerDiscoverPeripheral', handleDiscoverPeripheral)
    return () => {
      stopListener.remove()
      discoverListener.remove()
    }
  }, [])

  const requestPermissions = useCallback(async () => {
    if (Platform.OS === 'android') {
      const permissions = [
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_ADVERTISE,
      ]

      const granted = await PermissionsAndroid.requestMultiple(permissions)

      const allPermissionsGranted = Object.values(granted).every(
        (permission) => permission === PermissionsAndroid.RESULTS.GRANTED
      )

      return allPermissionsGranted
    }
    return true
  }, [])

  const startScan = useCallback(async () => {
    const permissionsGranted = await requestPermissions()
    console.log(permissionsGranted)
    if (permissionsGranted) {
      setDevices([]) // Clear devices list before scanning
      BleManager.scan([uuid], 10, true)
        .then(() => {
          setIsScanning(true)
        })
        .catch((err: any) => {
          console.error('Scan failed', err)
          setIsScanning(false)
        })
    } else {
      console.log('Permissions not granted')
    }
  }, [requestPermissions, devices])

  const connectToDevice = (deviceId: string) => {
    BleManager.connect(deviceId)
      .then(() => {
        console.log('Connected to', deviceId)
        setConnectedDeviceId(deviceId)
        Alert.alert('Connection Successful', `Connected to device ${deviceId}`)
      })
      .catch((err: any) => {
        console.error('Connection failed', err)
        Alert.alert('Connection Failed', `Failed to connect to device ${deviceId}`)
      })
  }

  const startAdvertising = async () => {
    setDiscoverable(true)
    console.log('permissionsGranted')
    const permissionsGranted = await requestPermissions()
    console.log(permissionsGranted)
    if (permissionsGranted) {
      try {
        await BleAdvertise.broadcast(uuid, [], {
          includeDeviceName: true,
        })
          .then((success) => {
            console.log(success)
          })
          .catch((error) => {
            console.log('broadcast failed with: ' + error)
          })
      } catch (error) {
        console.log('Broadcast failed with: ' + error)
        Alert.alert('Broadcast Failed', `Failed to start broadcasting: ${error}`)
      }
    } else {
      console.log('Permissions not granted')
      Alert.alert('Permissions Denied', 'Necessary permissions are not granted')
    }
  }

  const stopAdvertising = async () => {
    setDiscoverable(false)
    try {
      await BleAdvertise.stopBroadcast()
      console.log('Stopped advertising')
    } catch (error) {
      console.error('Failed to stop advertising:', error)
    }
  }

  const renderItem = ({ item }: { item: LocalDevice }) => (
    <View style={styles.device}>
      <Text style={[TextTheme.title]}>{item.name}</Text>
      <Button title="Connect" onPress={() => connectToDevice(item.id)} buttonType={ButtonType.Secondary} />
    </View>
  )

  return (
    <SafeAreaView style={styles.container}>
      <View
        style={{
          flexDirection: 'row',
          justifyContent: 'space-between',
          marginVertical: 20,
        }}
      >
        <View style={{ flexShrink: 1, marginRight: 10, justifyContent: 'center' }}>
          <Text style={[TextTheme.bold]}>{t('ScanBLE.MakeDiscoverable')}</Text>
        </View>
        <View style={{ justifyContent: 'center' }}>
          <Pressable
            testID={testIdWithKey('ToggleBluetooth')}
            accessible
            accessibilityLabel={t('ScanBLE.Toggle')}
            accessibilityRole={'switch'}
          >
            <Switch
              trackColor={{ false: ColorPallet.grayscale.lightGrey, true: ColorPallet.brand.primaryDisabled }}
              thumbColor={discoverable ? ColorPallet.brand.primary : ColorPallet.grayscale.mediumGrey}
              ios_backgroundColor={ColorPallet.grayscale.lightGrey}
              onValueChange={(value) => (value ? startAdvertising() : stopAdvertising())}
              value={discoverable}
              // disabled={!biometryAvailable}
            />
          </Pressable>
        </View>
      </View>
      <View style={{ marginBottom: 10 }}>
        <Text style={[TextTheme.normal]}>{t('ScanBLE.Text1')}</Text>
      </View>
      {!isScanning && devices.length === 0 && <Text style={styles.noDevicesText}>No devices found.</Text>}
      <FlatList data={devices} renderItem={renderItem} keyExtractor={(item) => item.id} />
      {connectedDeviceId && <Text>Connected to device: {connectedDeviceId}</Text>}
      <Button
        title={t('ScanBLE.ScanDevices')}
        onPress={startScan}
        disabled={isScanning}
        buttonType={ButtonType.Primary}
      >
        {isScanning && <ButtonLoading />}
      </Button>
      {/* <Button title="Advertise as peripheral" onPress={startBLEAdvertising} buttonType={ButtonType.Primary} /> */}
    </SafeAreaView>
  )
}

export default ScanBLE

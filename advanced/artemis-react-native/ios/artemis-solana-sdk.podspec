Pod::Spec.new do |s|
  s.name         = "artemis-solana-sdk"
  s.version      = "1.0.9"
  s.summary      = "Artemis Solana SDK for React Native - iOS Native Module"
  s.description  = <<-DESC
    A modular, mobile-first Solana SDK for React Native.
    Provides Base58 encoding/decoding, cryptographic utilities, and Solana-specific
    validation functions. Cross-platform parity with the Android Kotlin implementation.
  DESC
  s.homepage     = "https://github.com/QuarksBlueFoot/Selenus-Artemis-Solana-SDK-"
  s.license      = { :type => "Apache-2.0", :file => "../LICENSE" }
  s.author       = { "Bluefoot Labs" => "contact@bluefootlabs.com" }
  s.platforms    = { :ios => "13.0" }
  s.source       = { :git => "https://github.com/QuarksBlueFoot/Selenus-Artemis-Solana-SDK-.git", :tag => "v#{s.version}" }
  s.source_files = "*.{h,m,swift}"
  s.swift_version = "5.0"
  
  s.dependency "React-Core"
  
  # Note: MWA (Mobile Wallet Adapter) is Android-only as it relies on Solana Mobile Stack.
  # iOS apps should use WalletConnect or other iOS-compatible wallet connection protocols.
  # This module provides cross-platform Base58 and crypto utilities.
end

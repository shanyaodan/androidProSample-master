package com.example.sampleandroid.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;

import android.annotation.SuppressLint;
import android.net.SSLCertificateSocketFactory;
import android.os.Build;

/**
 * Custom SSLSocketFactory that adds Self-Signed (trusted) certificates, and SNI
 * support.
 *
 * Loosely based on:
 * http://blog.dev001.net/post/67082904181/android-using-sni-and
 * -tlsv1-2-with-apache-httpclient
 *
 * Ref: https://github.com/wordpress-mobile/WordPress-Android/issues/1288
 *
 */
public class TrustUserSSLCertsSocketFactory extends SSLSocketFactory {
	private SSLCertificateSocketFactory mFactory;
	private static final BrowserCompatHostnameVerifier mHostnameVerifier = new BrowserCompatHostnameVerifier();

	public TrustUserSSLCertsSocketFactory() throws IOException,
			GeneralSecurityException {
		super(null);
		// No handshake timeout used
		mFactory = (SSLCertificateSocketFactory) SSLCertificateSocketFactory
				.getDefault(0);
		TrustManager[] trustAllowedCerts;
		try {
			trustAllowedCerts = new TrustManager[] { new WPTrustManager(
					SelfSignedSSLCertsManager.getInstance(null)
							.getLocalKeyStore()) };
			mFactory.setTrustManagers(trustAllowedCerts);
		} catch (GeneralSecurityException e1) {
		}
	}

	public static SocketFactory getDefault() throws IOException,
			GeneralSecurityException {
		return new TrustUserSSLCertsSocketFactory();
	}

	public Socket createSocket() throws IOException {
		return mFactory.createSocket();
	}

	@Override
	public Socket createSocket(Socket plainSocket, String host, int port,
			boolean autoClose) throws IOException {
		if (autoClose) {
			// we don't need the plainSocket
			plainSocket.close();
		}

		SSLSocket ssl = (SSLSocket) mFactory.createSocket(
				InetAddress.getByName(host), port);
		return enableSNI(ssl, host);
	}

	@SuppressLint("NewApi")
	private Socket enableSNI(SSLSocket ssl, String host)
			throws SSLPeerUnverifiedException {
		// enable TLSv1.1/1.2 if available
		// (see https://github.com/rfc2822/davdroid/issues/229)
		ssl.setEnabledProtocols(ssl.getSupportedProtocols());

		// set up SNI before the handshake
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			mFactory.setHostname(ssl, host);
		} else {
			try {
				java.lang.reflect.Method setHostnameMethod = ssl.getClass()
						.getMethod("setHostname", String.class);
				setHostnameMethod.invoke(ssl, host);
			} catch (Exception e) {
			}
		}

		// verify hostname and certificate
		SSLSession session = ssl.getSession();
		if (!mHostnameVerifier.verify(host, session)) {
			// the verify failed. We need to check if the current certificate is
			// in Trusted Store.
			try {
				Certificate[] errorChain = ssl.getSession()
						.getPeerCertificates();
				X509Certificate[] X509CertificateChain = new X509Certificate[errorChain.length];
				for (int i = 0; i < errorChain.length; i++) {
					X509Certificate x509Certificate = (X509Certificate) errorChain[0];
					X509CertificateChain[i] = x509Certificate;
				}

				if (X509CertificateChain.length == 0) {
					throw new SSLPeerUnverifiedException(
							"Cannot verify hostname: " + host);
				}

				if (!SelfSignedSSLCertsManager.getInstance(null)
						.isCertificateTrusted(X509CertificateChain[0])) {
					SelfSignedSSLCertsManager.getInstance(null)
							.setLastFailureChain(X509CertificateChain);
					throw new SSLPeerUnverifiedException(
							"Cannot verify hostname: " + host);
				}
			} catch (GeneralSecurityException e) {

				throw new SSLPeerUnverifiedException("Cannot verify hostname: "
						+ host);
			} catch (IOException e) {

				throw new SSLPeerUnverifiedException("Cannot verify hostname: "
						+ host);
			} catch (Exception e) {
				// We don't want crash the app here for an unexpected error

				throw new SSLPeerUnverifiedException("Cannot verify hostname: "
						+ host);
			}
		}

		return ssl;
	}

	public Socket createSocket(InetAddress inaddr, int i, InetAddress inaddr1,
			int j) throws IOException {
		SSLSocket ssl = (SSLSocket) mFactory
				.createSocket(inaddr, i, inaddr1, j);
		return enableSNI(ssl, inaddr.getHostName());
	}

	public Socket createSocket(InetAddress inaddr, int i) throws IOException {
		SSLSocket ssl = (SSLSocket) mFactory.createSocket(inaddr, i);
		return enableSNI(ssl, inaddr.getHostName());
	}

	public Socket createSocket(String s, int i, InetAddress inaddr, int j)
			throws IOException {
		SSLSocket ssl = (SSLSocket) mFactory.createSocket(s, i, inaddr, j);
		return enableSNI(ssl, inaddr.getHostName());
	}

	public Socket createSocket(String s, int i) throws IOException {
		SSLSocket ssl = (SSLSocket) mFactory.createSocket(s, i);
		return enableSNI(ssl, s);
	}

	public String[] getDefaultCipherSuites() {
		return mFactory.getDefaultCipherSuites();
	}

	public String[] getSupportedCipherSuites() {
		return mFactory.getSupportedCipherSuites();
	}
}

package com.example.sampleandroid.http;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.http.SslCertificate;
import android.os.Bundle;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

import com.example.sampleandroid.R;
import com.example.sampleandroid.util.Constants;

public class SelfSignedSSLCertsManager {
	private static SelfSignedSSLCertsManager sInstance;
	private File mLocalTrustStoreFile;
	private KeyStore mLocalKeyStore;
	public static final String DB_SECRET = "wordpress";
	// Used to hold the last self-signed certificate chain that doesn't pass
	// trusting
	private X509Certificate[] mLastFailureChain;

	private SelfSignedSSLCertsManager(Context ctx) throws IOException,
			GeneralSecurityException {
		mLocalTrustStoreFile = new File(ctx.getFilesDir(),
				"self_signed_certs_truststore.bks");
		createLocalKeyStoreFile();
		mLocalKeyStore = loadTrustStore(ctx);
	}

	public static void askForSslTrust(final Context ctx,
			final GenericCallback<Void> certificateTrusted) {
		AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
		alert.setTitle(ctx.getString(R.string.ssl_certificate_error));
		alert.setMessage(ctx.getString(R.string.ssl_certificate_ask_trust));
		alert.setPositiveButton(R.string.yes,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						SelfSignedSSLCertsManager selfSignedSSLCertsManager;
						try {
							selfSignedSSLCertsManager = SelfSignedSSLCertsManager
									.getInstance(ctx);
							selfSignedSSLCertsManager
									.addCertificates(selfSignedSSLCertsManager
											.getLastFailureChain());
						} catch (GeneralSecurityException e) {
						} catch (IOException e) {

						}
						if (certificateTrusted != null) {
							certificateTrusted.callback(null);
						}
					}
				});
		alert.setNeutralButton(R.string.ssl_certificate_details,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						viewSSLCerts(ctx);
					}
				});
		alert.setNegativeButton(R.string.no,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
					}
				});
		alert.show();
	}

	public static synchronized SelfSignedSSLCertsManager getInstance(Context ctx)
			throws IOException, GeneralSecurityException {
		if (sInstance == null) {
			sInstance = new SelfSignedSSLCertsManager(ctx);
		}
		return sInstance;
	}

	public void addCertificates(X509Certificate[] certs) throws IOException,
			GeneralSecurityException {
		if (certs == null || certs.length == 0) {
			return;
		}

		for (X509Certificate cert : certs) {
			String alias = hashName(cert.getSubjectX500Principal());
			mLocalKeyStore.setCertificateEntry(alias, cert);
		}
		saveTrustStore();

	}

	public void addCertificate(X509Certificate cert) throws IOException,
			GeneralSecurityException {
		if (cert == null) {
			return;
		}

		String alias = hashName(cert.getSubjectX500Principal());
		mLocalKeyStore.setCertificateEntry(alias, cert);
		saveTrustStore();
	}

	public KeyStore getLocalKeyStore() {
		return mLocalKeyStore;
	}

	private KeyStore loadTrustStore(Context ctx) throws IOException,
			GeneralSecurityException {
		KeyStore localTrustStore = KeyStore.getInstance("BKS");
		InputStream in = new FileInputStream(mLocalTrustStoreFile);
		try {
			localTrustStore.load(in, Constants.DB_SECRET.toCharArray());
		} finally {
			in.close();
		}
		return localTrustStore;
	}

	private void saveTrustStore() throws IOException, GeneralSecurityException {
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(mLocalTrustStoreFile);
			mLocalKeyStore.store(out, Constants.DB_SECRET.toCharArray());
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {

				}
			}
		}
	}

	/**
	 * Create an empty trust store file if missing
	 */
	private void createLocalKeyStoreFile() throws GeneralSecurityException,
			IOException {
		if (!mLocalTrustStoreFile.exists()) {
			FileOutputStream out = null;
			try {
				out = new FileOutputStream(mLocalTrustStoreFile);
				KeyStore localTrustStore = KeyStore.getInstance("BKS");
				localTrustStore.load(null, Constants.DB_SECRET.toCharArray());
				localTrustStore.store(out, Constants.DB_SECRET.toCharArray());
			} finally {
				if (out != null) {
					try {
						out.close();
					} catch (IOException e) {
					}
				}
			}
		}
	}

	public void emptyLocalKeyStoreFile() {
		if (mLocalTrustStoreFile.exists()) {
			mLocalTrustStoreFile.delete();
		}
		try {
			createLocalKeyStoreFile();
		} catch (GeneralSecurityException e) {

		} catch (IOException e) {

		}
	}

	private static String hashName(X500Principal principal) {
		try {
			byte[] digest = MessageDigest.getInstance("MD5").digest(
					principal.getEncoded());
			String result = Integer.toString(leInt(digest), 16);
			if (result.length() > 8) {
				StringBuilder buff = new StringBuilder();
				int padding = 8 - result.length();
				for (int i = 0; i < padding; i++) {
					buff.append("0");
				}
				buff.append(result);

				return buff.toString();
			}

			return result;
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	private static int leInt(byte[] bytes) {
		int offset = 0;
		return ((bytes[offset++] & 0xff) << 0)
				| ((bytes[offset++] & 0xff) << 8)
				| ((bytes[offset++] & 0xff) << 16)
				| ((bytes[offset] & 0xff) << 24);
	}

	public X509Certificate[] getLastFailureChain() {
		return mLastFailureChain;
	}

	public void setLastFailureChain(X509Certificate[] lastFaiulreChain) {
		mLastFailureChain = lastFaiulreChain;
	}

	public String getLastFailureChainDescription() {
		return (mLastFailureChain == null || mLastFailureChain.length == 0) ? ""
				: mLastFailureChain[0].toString();
	}

	public boolean isCertificateTrusted(SslCertificate cert) {
		if (cert == null)
			return false;

		Bundle bundle = SslCertificate.saveState(cert);
		X509Certificate x509Certificate;
		byte[] bytes = bundle.getByteArray("x509-certificate");
		if (bytes == null) {
			x509Certificate = null;
		} else {
			try {
				CertificateFactory certFactory = CertificateFactory
						.getInstance("X.509");
				Certificate certX509 = certFactory
						.generateCertificate(new ByteArrayInputStream(bytes));
				x509Certificate = (X509Certificate) certX509;
			} catch (CertificateException e) {
				x509Certificate = null;
			}
		}

		return isCertificateTrusted(x509Certificate);
	}

	public boolean isCertificateTrusted(X509Certificate x509Certificate) {
		if (x509Certificate == null)
			return false;

		// Now I have an X509Certificate I can pass to an X509TrustManager for
		// validation.
		try {
			String certificateAlias = this.getLocalKeyStore()
					.getCertificateAlias(x509Certificate);
			if (certificateAlias != null) {
				return true;
			}
		} catch (KeyStoreException e) {
		}

		return false;
	}

	public static void viewSSLCerts(Context context) {
		try {
			Intent intent = new Intent(context, SSLCertsViewActivity.class);
			SelfSignedSSLCertsManager selfSignedSSLCertsManager = SelfSignedSSLCertsManager
					.getInstance(context);
			String lastFailureChainDescription = selfSignedSSLCertsManager
					.getLastFailureChainDescription().replaceAll("\n", "<br/>");
			intent.putExtra(SSLCertsViewActivity.CERT_DETAILS_KEYS,
					lastFailureChainDescription);
			context.startActivity(intent);
		} catch (GeneralSecurityException e) {

		} catch (IOException e) {

		}
	}

}

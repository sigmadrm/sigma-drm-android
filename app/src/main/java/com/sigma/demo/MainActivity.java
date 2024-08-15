package com.sigma.demo;

import androidx.annotation.NonNull;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.ErrorMessageProvider;
import com.google.android.exoplayer2.util.Util;

import com.sigma.player.playlist.DefaultHlsPlaylistParserFactory;
import com.sigma.drm.SigmaHelper;
import com.sigma.player.HlsMediaSource;

public class MainActivity extends Activity implements StyledPlayerView.ControllerVisibilityListener {
  protected StyledPlayerView playerView;
  protected ExoPlayer player;
  private MediaSource mediaSource;
  RenderersFactory renderersFactory;

  private int startWindow = 0;
  private long startPosition = 0;

  private String MEDIA_URL = "MEDIA_URL";
  private String MERCHANT_ID = "MERCHANT_ID";
  private String APP_ID = "APP_ID";
  private String USER_ID = "USER_ID";
  private String SESSION_ID = "SESSION_ID";

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    playerView = findViewById(R.id.player_view);
    playerView.setControllerVisibilityListener(this);
    playerView.setErrorMessageProvider(new PlayerErrorMessageProvider());
    playerView.requestFocus();

    SigmaHelper.instance().init();
  }

  @Override
  public void onStart() {
    super.onStart();
    if (Util.SDK_INT > 23) {
      initializePlayer();
      if (playerView != null) {
        playerView.onResume();
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if (Util.SDK_INT <= 23 || player == null) {
      initializePlayer();
      if (playerView != null) {
        playerView.onResume();
      }
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      if (playerView != null) {
        playerView.onPause();
      }
      releasePlayer();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      if (playerView != null) {
        playerView.onPause();
      }
      releasePlayer();
    }
  }

  private void releasePlayer() {
    if (playerView != null) {
      playerView.onPause();
    }
    if (player != null) {
      player.release();
      player = null;
      mediaSource = null;
    }
  }

  protected void initializePlayer() {
    SigmaHelper.instance().configure(MERCHANT_ID, APP_ID, USER_ID, SESSION_ID);

    if (player == null) {
      mediaSource = createMediaSource(Uri.parse(MEDIA_URL), "", null);
      ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(this);
      playerBuilder.setRenderersFactory(getRenderersFactory());
      player = playerBuilder.build();
      player.setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true);
      player.setPlayWhenReady(true);
      player.addListener(new Player.Listener() {
        public void onPlayerError(PlaybackException error) {
          if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            // Re-initialize player at the live edge.
            player.seekToDefaultPosition();
            player.prepare();
          } else {
            // Handle other errors
          }
        }
      });
      playerView.setPlayer(player);
    }

    boolean haveStartPosition = startWindow != C.INDEX_UNSET;
    if (haveStartPosition) {
      player.seekTo(startWindow, startPosition);
    }
    player.prepare(mediaSource, !haveStartPosition, false);
  }

  private MediaSource createMediaSource(Uri uri, String extension, DrmSessionManager drmSessionManager) {
    @C.ContentType
    int type = Util.inferContentType(uri, extension);
    DataSource.Factory dataSourceFactory = buildDataSourceFactory();
    switch (type) {
      case C.CONTENT_TYPE_DASH:
        return new DashMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri));
      case C.CONTENT_TYPE_SS:
        return new SsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri));
      case C.CONTENT_TYPE_HLS:
        return new HlsMediaSource.Factory(dataSourceFactory)
            .setPlaylistParserFactory(new DefaultHlsPlaylistParserFactory())
            .createMediaSource(MediaItem.fromUri(uri));
      case C.CONTENT_TYPE_OTHER:
        return new ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri));
      default:
        throw new IllegalStateException("Unsupported type: " + type);
    }
  }

  private DataSource.Factory buildDataSourceFactory() {
    return new DefaultDataSourceFactory(this, buildHttpDataSourceFactory());
  }

  public HttpDataSource.Factory buildHttpDataSourceFactory() {
    return new DefaultHttpDataSource.Factory().setUserAgent("SigmaDRM");
  }

  private RenderersFactory getRenderersFactory() {
    if (renderersFactory == null) {
      renderersFactory = new DefaultRenderersFactory(getApplicationContext())
          .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
    }
    return renderersFactory;
  }

  @Override
  public void onVisibilityChanged(int i) {
    // Do something
  }

  private class PlayerErrorMessageProvider implements ErrorMessageProvider<PlaybackException> {
    @Override
    public Pair<Integer, String> getErrorMessage(PlaybackException error) {
      String errorCode = error.errorCode + ":" + error.getErrorCodeName();
      Log.e("SigmaPlayer Error ", " ErrorCode " + errorCode);
      return Pair.create(0, errorCode);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (grantResults.length == 0) {
      // Empty results are triggered if a permission is requested while another
      // request was already
      // pending and can be safely ignored in this case.
      return;
    }
    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      initializePlayer();
    } else {
      showToast(R.string.storage_permission_denied);
      finish();
    }
  }

  private void showToast(int messageId) {
    showToast(getString(messageId));
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }
}

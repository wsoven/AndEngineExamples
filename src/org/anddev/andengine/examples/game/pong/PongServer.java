package org.anddev.andengine.examples.game.pong;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

import org.anddev.andengine.engine.handler.IUpdateHandler;
import org.anddev.andengine.entity.primitive.Line;
import org.anddev.andengine.entity.primitive.Rectangle;
import org.anddev.andengine.examples.game.pong.adt.MovePaddleClientMessage;
import org.anddev.andengine.examples.game.pong.adt.PaddleUserData;
import org.anddev.andengine.examples.game.pong.adt.Score;
import org.anddev.andengine.examples.game.pong.adt.SetPaddleIDServerMessage;
import org.anddev.andengine.examples.game.pong.adt.UpdateBallServerMessage;
import org.anddev.andengine.examples.game.pong.adt.UpdatePaddleServerMessage;
import org.anddev.andengine.examples.game.pong.adt.UpdateScoreServerMessage;
import org.anddev.andengine.examples.game.pong.util.constants.PongConstants;
import org.anddev.andengine.extension.multiplayer.protocol.adt.message.client.BaseClientMessage;
import org.anddev.andengine.extension.multiplayer.protocol.server.BaseClientConnectionListener;
import org.anddev.andengine.extension.multiplayer.protocol.server.BaseClientMessageSwitch;
import org.anddev.andengine.extension.multiplayer.protocol.server.BaseServer;
import org.anddev.andengine.extension.multiplayer.protocol.server.BaseServer.IServerStateListener.DefaultServerStateListener;
import org.anddev.andengine.extension.multiplayer.protocol.server.ClientConnection;
import org.anddev.andengine.extension.multiplayer.protocol.server.ClientMessageExtractor;
import org.anddev.andengine.extension.physics.box2d.FixedStepPhysicsWorld;
import org.anddev.andengine.extension.physics.box2d.PhysicsFactory;
import org.anddev.andengine.extension.physics.box2d.PhysicsWorld;
import org.anddev.andengine.extension.physics.box2d.util.Vector2Pool;
import org.anddev.andengine.extension.physics.box2d.util.constants.PhysicsConstants;
import org.anddev.andengine.util.Debug;
import org.anddev.andengine.util.MathUtils;

import android.util.SparseArray;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef.BodyType;
import com.badlogic.gdx.physics.box2d.Contact;
import com.badlogic.gdx.physics.box2d.ContactListener;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;

/**
 * @author Nicolas Gramlich
 * @since 20:00:09 - 28.02.2011
 */
public class PongServer extends BaseServer<ClientConnection> implements IUpdateHandler, PongConstants, ContactListener {
	// ===========================================================
	// Constants
	// ===========================================================

	private static final FixtureDef PADDLE_FIXTUREDEF = PhysicsFactory.createFixtureDef(1, 1, 0);
	private static final FixtureDef BALL_FIXTUREDEF = PhysicsFactory.createFixtureDef(1, 1, 0);
	private static final FixtureDef WALL_FIXTUREDEF = PhysicsFactory.createFixtureDef(1, 1, 0);

	// ===========================================================
	// Fields
	// ===========================================================

	private final PhysicsWorld mPhysicsWorld;
	private final Body mBallBody;
	private final SparseArray<Body> mPaddleBodies = new SparseArray<Body>();
	private boolean mResetBall = true;
	private final SparseArray<Score> mPaddleScores = new SparseArray<Score>();

	// ===========================================================
	// Constructors
	// ===========================================================

	public PongServer(final BaseClientConnectionListener pClientConnectionListener) {
		super(SERVER_PORT, pClientConnectionListener, new DefaultServerStateListener());

		this.mPaddleScores.put(PADDLE_LEFT.getOwnerID(), new Score());
		this.mPaddleScores.put(PADDLE_RIGHT.getOwnerID(), new Score());

		this.mPhysicsWorld = new FixedStepPhysicsWorld(FPS, 2, new Vector2(0, 0), false, 8, 8);

		this.mPhysicsWorld.setContactListener(this);

		/* Ball */
		this.mBallBody = PhysicsFactory.createCircleBody(this.mPhysicsWorld, new Rectangle(-BALL_WIDTH_HALF, -BALL_HEIGHT_HALF, BALL_WIDTH, BALL_HEIGHT), BodyType.DynamicBody, BALL_FIXTUREDEF);
		this.mBallBody.setBullet(true);

		/* Paddles */
		final Body paddleBodyLeft = PhysicsFactory.createBoxBody(this.mPhysicsWorld, new Rectangle(-GAME_WIDTH_HALF, -PADDLE_HEIGHT_HALF, PADDLE_WIDTH, PADDLE_HEIGHT), BodyType.KinematicBody, PADDLE_FIXTUREDEF);
		paddleBodyLeft.setUserData(PADDLE_LEFT);
		this.mPaddleBodies.put(PADDLE_LEFT.getOwnerID(), paddleBodyLeft);

		final Body paddleBodyRight = PhysicsFactory.createBoxBody(this.mPhysicsWorld, new Rectangle(GAME_WIDTH_HALF - PADDLE_WIDTH, -PADDLE_HEIGHT_HALF, PADDLE_WIDTH, PADDLE_HEIGHT), BodyType.KinematicBody, PADDLE_FIXTUREDEF);
		paddleBodyRight.setUserData(PADDLE_RIGHT);
		this.mPaddleBodies.put(PADDLE_RIGHT.getOwnerID(), paddleBodyRight);

		this.initWalls();
	}

	private void initWalls() {
		final Line left = new Line(-GAME_WIDTH_HALF, -GAME_HEIGHT_HALF, -GAME_WIDTH_HALF, GAME_HEIGHT_HALF);
		final Line right = new Line(GAME_WIDTH_HALF, -GAME_HEIGHT_HALF, GAME_WIDTH_HALF, GAME_HEIGHT_HALF);

		WALL_FIXTUREDEF.isSensor = true;

		final Body leftBody = PhysicsFactory.createLineBody(this.mPhysicsWorld, left, WALL_FIXTUREDEF);
		leftBody.setUserData(this.mPaddleBodies.get(PADDLE_LEFT.getOwnerID()));

		final Body rightBody = PhysicsFactory.createLineBody(this.mPhysicsWorld, right, WALL_FIXTUREDEF);
		rightBody.setUserData(this.mPaddleBodies.get(PADDLE_RIGHT.getOwnerID()));


		WALL_FIXTUREDEF.isSensor = false;

		final Line top = new Line(-GAME_WIDTH_HALF, -GAME_HEIGHT_HALF, GAME_WIDTH_HALF, -GAME_HEIGHT_HALF);
		final Line bottom = new Line(-GAME_WIDTH_HALF, GAME_HEIGHT_HALF, GAME_WIDTH_HALF, GAME_HEIGHT_HALF);

		PhysicsFactory.createLineBody(this.mPhysicsWorld, top, WALL_FIXTUREDEF);
		PhysicsFactory.createLineBody(this.mPhysicsWorld, bottom, WALL_FIXTUREDEF);
	}

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================

	@Override
	public void beginContact(final Contact pContact) {
		final Fixture fixtureA = pContact.getFixtureA();
		final Body bodyA = fixtureA.getBody();
		final Object userDataA = bodyA.getUserData();

		final Fixture fixtureB = pContact.getFixtureB();
		final Body bodyB = fixtureB.getBody();
		final Object userDataB = bodyB.getUserData();

		final boolean isScoreSensorA = userDataA != null && userDataA instanceof Body;
		final boolean isScoreSensorB = userDataB != null && userDataB instanceof Body;

		if(isScoreSensorA || isScoreSensorB) {
			this.mResetBall = true;

			final PaddleUserData paddleUserData = (isScoreSensorA) ? (PaddleUserData)(((Body)userDataA).getUserData()) : (PaddleUserData)(((Body)userDataA).getUserData());

			final int opponentID = paddleUserData.getOpponentID();
			final Score opponentPaddleScore = this.mPaddleScores.get(opponentID);
			opponentPaddleScore.increase();

			// TODO Pooling
			final UpdateScoreServerMessage updateScoreServerMessage = new UpdateScoreServerMessage(opponentID, opponentPaddleScore.getScore());

			final ArrayList<ClientConnection> clientConnections = this.mClientConnections;
			for(int i = 0; i < clientConnections.size(); i++) {
				try {
					final ClientConnection clientConnection = clientConnections.get(i);
					clientConnection.sendServerMessage(updateScoreServerMessage);
				} catch (final IOException e) {
					Debug.e(e);
				}
			}
		}
	}

	@Override
	public void endContact(final Contact pContact) {

	}

	@Override
	public void onUpdate(final float pSecondsElapsed) {
		if(this.mResetBall) {
			this.mResetBall = false;
			final Vector2 vector2 = Vector2Pool.obtain(0, 0);
			this.mBallBody.setTransform(vector2, 0);

			vector2.set(MathUtils.randomSign() * MathUtils.random(3, 4), MathUtils.randomSign() * MathUtils.random(3, 4));
			this.mBallBody.setLinearVelocity(vector2);
			Vector2Pool.recycle(vector2);
		}
		this.mPhysicsWorld.onUpdate(pSecondsElapsed);

		// TODO Pooling

		final ArrayList<ClientConnection> clientConnections = this.mClientConnections;
		for(int i = 0; i < clientConnections.size(); i++) {
			try {
				/* Update Ball. */
				final ClientConnection clientConnection = clientConnections.get(i);
				final Vector2 ballPosition = this.mBallBody.getPosition();
				final float ballX = ballPosition.x * PhysicsConstants.PIXEL_TO_METER_RATIO_DEFAULT - BALL_WIDTH_HALF;
				final float ballY = ballPosition.y * PhysicsConstants.PIXEL_TO_METER_RATIO_DEFAULT - BALL_HEIGHT_HALF;

				clientConnection.sendServerMessage(new UpdateBallServerMessage(ballX, ballY));

				/* Update Paddles. */
				final SparseArray<Body> paddleBodies = this.mPaddleBodies;
				for(int j = 0; j < paddleBodies.size(); j++) {
					final int paddleID = paddleBodies.keyAt(j);
					final Body paddleBody = paddleBodies.get(paddleID);
					final Vector2 paddlePosition = paddleBody.getPosition();

					final float paddleX = paddlePosition.x * PhysicsConstants.PIXEL_TO_METER_RATIO_DEFAULT - PADDLE_WIDTH_HALF;
					final float paddleY = paddlePosition.y * PhysicsConstants.PIXEL_TO_METER_RATIO_DEFAULT - PADDLE_HEIGHT_HALF;
					clientConnection.sendServerMessage(new UpdatePaddleServerMessage(paddleID, paddleX, paddleY));
				}
			} catch (final IOException e) {
				Debug.e(e);
			}
		}
	}

	@Override
	public void reset() {
		/* Nothing. */
	}

	@Override
	protected ClientConnection newClientConnection(final Socket pClientSocket, final BaseClientConnectionListener pClientConnectionListener) throws Exception {
		final ClientConnection clientConnection = new ClientConnection(pClientSocket, pClientConnectionListener,
				new ClientMessageExtractor(){
			@Override
			public BaseClientMessage readMessage(final short pFlag, final DataInputStream pDataInputStream) throws IOException {
				switch(pFlag) {
					case FLAG_MESSAGE_CLIENT_MOVE_PADDLE:
						return new MovePaddleClientMessage(pDataInputStream);
					default:
						return super.readMessage(pFlag, pDataInputStream);
				}
			}
		},
		new BaseClientMessageSwitch() {
			@Override
			public void switchMessage(final ClientConnection pClientConnection, final BaseClientMessage pClientMessage) throws IOException {
				switch(pClientMessage.getFlag()) {
					case FLAG_MESSAGE_CLIENT_MOVE_PADDLE:
						final MovePaddleClientMessage movePaddleClientMessage = (MovePaddleClientMessage)pClientMessage;
						final Body paddleBody = PongServer.this.mPaddleBodies.get(movePaddleClientMessage.mPaddleID);
						final Vector2 paddlePosition = paddleBody.getTransform().getPosition();
						final float paddleY = MathUtils.bringToBounds(-GAME_HEIGHT_HALF + PADDLE_HEIGHT_HALF, GAME_HEIGHT_HALF - PADDLE_HEIGHT_HALF, movePaddleClientMessage.mY);
						paddlePosition.set(paddlePosition.x, paddleY / PhysicsConstants.PIXEL_TO_METER_RATIO_DEFAULT);
						paddleBody.setTransform(paddlePosition, 0);
						break;
					default:
						super.switchMessage(pClientConnection, pClientMessage);
				}
			}
		}
		);
		clientConnection.sendServerMessage(new SetPaddleIDServerMessage(this.mClientConnections.size())); // TODO should not be size();
		return clientConnection;
	}

	// ===========================================================
	// Methods
	// ===========================================================

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
}

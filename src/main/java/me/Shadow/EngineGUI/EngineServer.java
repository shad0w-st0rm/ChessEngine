package me.Shadow.EngineGUI;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import me.Shadow.Engine.OpeningBook;
import me.Shadow.Engine.PrecomputedData;
import me.Shadow.Engine.PrecomputedMagicNumbers;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class EngineServer
{
	public static void main(String[] args)
	{
		SpringApplication.run(EngineServer.class, args);
		precomputeEngineValues();
	}

	public static void precomputeEngineValues()
	{
		PrecomputedData.generateData();
		PrecomputedMagicNumbers.precomputeMagics();
		try
		{
			OpeningBook.createBookFromBinary("LichessOpeningBookBinary.dat");
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	@Bean
	public ServletWebServerFactory servletContainer()
	{
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		factory.addAdditionalTomcatConnectors(createHttpConnector());
		return factory;
	}

	private Connector createHttpConnector()
	{
		Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
		connector.setScheme("http");
		connector.setPort(8080); // HTTP port
		connector.setRedirectPort(8443); // Redirect HTTP to HTTPS port
		return connector;
	}
}

@RestController
@CrossOrigin(origins = {"http://localhost:8081", "https://chess-engine--preview.expo.app", "https://chess-engine.expo.app", "https://chess-engine-shadow.netlify.app"}) // Enable CORS for this controller
class EngineServerController
{
	@Autowired
	private EngineService engineService;

	private static final Logger logger = LoggerFactory.getLogger(EngineServerController.class);

	@PostMapping("/newgame")
	public CompletableFuture<ResponseEntity<GamePlayer>> newGame(@RequestBody String command)
	{
		try
		{
			return engineService.newGame(command);
		}
		catch (Error e)
		{
			logger.info("Error creating a new game: command={}", command);
			return CompletableFuture.completedFuture(new ResponseEntity<>(null, HttpStatus.BAD_REQUEST));
		}

	}

	@GetMapping("/playermove")
	public CompletableFuture<ResponseEntity<String>> playerMove(@RequestParam Integer playerID,
			@RequestParam String move)
	{
		return engineService.playerMove(playerID, move);
	}

	@GetMapping("/bestmove")
	public CompletableFuture<ResponseEntity<String>> bestMove(@RequestParam Integer playerID,
			@RequestParam Optional<Integer> movetime, @RequestParam Optional<Integer> wtime,
			@RequestParam Optional<Integer> winc, @RequestParam Optional<Integer> btime,
			@RequestParam Optional<Integer> binc)
	{
		logger.info("Received best move search command: playerID={}", playerID);

		if (!movetime.isPresent() && (!wtime.isPresent() || !btime.isPresent()))
		{
			return CompletableFuture.completedFuture(new ResponseEntity<String>(
					"Error: No missing movetime or clocktime values", HttpStatus.BAD_REQUEST));
		}
		else if (movetime.isPresent())
		{
			return engineService.bestMove(playerID, movetime.get());
		}
		else
		{
			return engineService.bestMove(playerID, wtime.get(), winc.orElse(0), btime.get(), binc.orElse(0));
		}
	}

	@GetMapping("/stopsearching")
	public CompletableFuture<ResponseEntity<String>> stopSearching(@RequestParam Integer playerID)
	{
		return engineService.stopSearching(playerID);
	}

	@GetMapping("/endgame")
	public CompletableFuture<ResponseEntity<String>> endGame(@RequestParam Integer playerID)
	{
		return engineService.endGame(playerID);
	}
	
	@GetMapping("/keepalive")
	public CompletableFuture<ResponseEntity<String>> keepAlive(@RequestParam Integer playerID)
	{
		return engineService.keepAlive(playerID);
	}
}

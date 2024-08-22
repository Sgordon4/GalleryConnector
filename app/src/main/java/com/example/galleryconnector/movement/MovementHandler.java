package com.example.galleryconnector.movement;

public class MovementHandler {

	public FileIOApi ioAPI;
	public DomainAPI domainAPI;


	//TODO Probably just move the ioAPI and domainAPI to the GalleryRepo.
	// This is here jic I end up needing space after I actually implement the queues and dbs.

	public static MovementHandler getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private static class SingletonHelper {
		private static final MovementHandler INSTANCE = new MovementHandler();
	}
	private MovementHandler() {
		ioAPI = FileIOApi.getInstance();
		domainAPI = DomainAPI.getInstance();
	}
}

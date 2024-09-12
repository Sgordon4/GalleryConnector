package com.example.galleryconnector;

import com.example.galleryconnector.movement.DomainAPI;

import org.junit.Test;

import java.util.UUID;

public class MaskTesting {

	@Test
	public void testOperations() {
		DomainAPI domainAPI = DomainAPI.getInstance();
		UUID fileUID = UUID.randomUUID();

		System.out.println("Testing Operations:");
		System.out.println("1: "+ Integer.toBinaryString(domainAPI.getMaskTESTING(fileUID)));

		domainAPI.queueOperation(DomainAPI.Operation.COPY_TO_LOCAL, fileUID);
		System.out.println("2: "+Integer.toBinaryString(domainAPI.getMaskTESTING(fileUID)));

		domainAPI.queueOperation(DomainAPI.Operation.COPY_TO_LOCAL, fileUID);
		System.out.println("3: "+Integer.toBinaryString(domainAPI.getMaskTESTING(fileUID)));

		domainAPI.queueOperation(DomainAPI.Operation.COPY_TO_SERVER, fileUID);
		System.out.println("4: "+Integer.toBinaryString(domainAPI.getMaskTESTING(fileUID)));

		domainAPI.queueOperation(DomainAPI.Operation.REMOVE_FROM_LOCAL, fileUID);
		System.out.println("5: "+Integer.toBinaryString(domainAPI.getMaskTESTING(fileUID)));
	}
}
